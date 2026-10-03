# Enterprise Event Log Streaming

Enable **Settings → Event Streaming** with a valid Enterprise license and `configuration:edit`. `configuration:get` permits viewing settings and health. The backend enforces both permission checks and the license gate. The default is disabled. Settings are persisted in Kviklet's existing configuration table, so no migration is required.

This feature writes activities to a dedicated UTF-8 JSON Lines stream. Each physical line contains one JSON object, followed by a newline. It never forwards these records to Kviklet's console logger. A local collector can tail the directory and send the records to your SIEM. The event envelope uses [Elastic Common Schema categorization](https://www.elastic.co/docs/reference/ecs/ecs-event); Kviklet-specific details live under `kviklet`, with `schema_version: "1.0.0"`. This is an ECS-aligned application schema, not a promise of a vendor-specific ingestion integration.

## Configuration and files

`GET /api/config/event-streaming` returns `{settings, status}`. `PUT` accepts these settings:

```json
{
  "enabled": true,
  "directory": "/var/log/kviklet/events",
  "maxFileSizeMiB": 10,
  "retentionDays": 180,
  "maxArchiveSizeMiB": 100,
  "loggingLevel": "FULL"
}
```

`loggingLevel` controls this application-wide stream. Only users with `configuration:edit` can change it. Missing values in existing saved settings or API requests default to `FULL`, preserving the previous behavior. Unknown values are rejected.

| Configuration view | API value | Coverage |
| --- | --- | --- |
| Level 1 — Security events only | `SECURITY_ONLY` | Authentication, permission denials, user and role changes, API keys, connection security settings, role-sync settings, and event-stream settings. Routine request/review activity, proxy sessions, and executions are omitted. |
| Level 2 — All events, without query text | `WITHOUT_QUERY_TEXT` | All covered activities, including access reasons, approvals, proxy activity, and result metadata. SQL statements and Kubernetes command text are omitted. |
| Level 3 — All events, with query text | `FULL` | All covered activities and fields, including access reasons, SQL statements, and Kubernetes commands. |

Security-only coverage uses an explicit action allowlist, not the broad ECS categories. New actions must be classified when introduced. Excluded events skip field preparation; Level 2 skips statement/command processing. The active level is checked again before a committed event is written, so a downgrade also filters events pending in a transaction. An upgrade does not restore text omitted during preparation. Level changes are captured by `event_stream.configuration_changed` with the previous and new settings, including at Level 1. Changes apply to future writes; existing files retain their contents. These settings do not alter the audit data stored in Kviklet or its normal application logs.

The examples use the public `/api` prefix supplied by the bundled web server. When connecting directly to the development backend on port 8081, omit `/api`. The directory must be an absolute path, writable by the application user, with no group or world write access. The directory itself and managed output files must not be symlinks. Newly created directories use mode 700; event files use 640. A collector may share the application's group for read access. A file lock prevents a second writer in the same directory; use local storage with reliable file locking and rename semantics.

The active file is `events.jsonl`. Logback rotates it at midnight UTC or the configured size threshold to `events.YYYY-MM-DD.N.jsonl`. Files are not compressed, allowing collectors to finish reading after rotation. The default file-size threshold is 10 MiB, retention is 180 days, and the archive budget is 100 MiB. File size is a rotation threshold: the final record may exceed it. Valid settings are 1–1024 MiB per file, 1–365 retention days, and an archive budget at least 1 MiB larger than the file-size threshold, up to 102400 MiB. This headroom covers one maximum-size record, including its terminating newline. Existing saved settings are preserved; a saved budget without this headroom reports degraded health until corrected, preventing cleanup under that configuration.

The archive budget **excludes the active file**. Cleanup runs asynchronously at startup, rotation, and during periodic maintenance, including idle periods. It is not a strict disk quota: budget for the active file, the largest event, cleanup lag, and the collector's own storage. Rotation uses Logback's [size and time based rolling policy](https://logback.qos.ch/manual/appenders.html). Kviklet applies retention in a single asynchronous directory scan at startup, after rotation, and roughly once per hour while idle. The scan includes all matching archives, including files left by prolonged downtime or a previously longer retention setting. Rotation and cleanup failures degrade stream health. Unrelated files are not retained or deleted by this policy. Disable streaming and save before changing the output directory, then re-enable streaming. Directory changes while enabled are rejected, including a combined disable-and-move request. The old directory remains untouched afterward; arrange its migration and cleanup separately.

## Delivery and health

Output is synchronous, serialized, immediate-flush, and best effort. A slow or stalled filesystem can delay a request; use local persistent storage. Successful activity changes are written after the database transaction commits. A rolled-back role change does not appear as a success. Settings saves are serialized through transaction completion separately from event writes. The file writer is opened only after a successful settings commit. A rolled-back save leaves destination files untouched. If the configured directory cannot be opened after commit, the saved settings remain in place and status reports degraded; correct the directory or permissions and retry. Live directory replacement is not supported. Execution attempts and completions are separate records correlated by the existing audit event ID. The original audit log's persistence and authorization behavior continue to apply.

There is no durable queue, retry of failed events, historical backfill, or SIEM delivery acknowledgment. A successful write flushes to the operating system, not an `fsync` durability guarantee. A crash, disk failure, or collector falling behind retention can lose events. On writer restart, an unterminated trailing line is discarded before appending, preserving earlier complete lines. During an output failure, application activities continue; the process counts detected failures, reports a sanitized health error, and retries opening after a 30-second backoff on subsequent activity, status checks, or periodic maintenance. Lost records are not replayed. Monitor stream health and collector freshness independently.

Status is one of `disabled`, `active`, `degraded`, or `license_expired`. `lastWriteAt`, `lastError`, and `detectedFailures` describe this process and reset on restart. There is no persisted sequence or completeness watermark. A valid license is checked roughly once per minute outside the writer lock; event capture uses the cached license and checks expiry before emitting. Disabled or unlicensed capture skips statement and result-metadata preparation. Expiry stops capture. An enabled stream resumes automatically after a valid renewal is detected, normally within one minute; a disabled stream remains disabled. A fresh process evaluates the current license and persisted setting.

## Records and coverage

Common fields are `@timestamp` (UTC event capture time), `ecs.version`, `service.name`, `service.version`, `service.ephemeral_id` (process lifetime UUID), `event.id` (record UUID), `event.kind`, `event.dataset`, `event.category`, `event.type`, `event.action`, `event.outcome`, and `kviklet.schema_version`. Identified actors include `user.id`, `user.name` (the saved full name, which can be null), `user.email`, and `user.roles` (a sorted array of assigned role names, empty when no roles are assigned). These fields snapshot the current Kviklet user record when the event is captured, including API-key and database-proxy activity. Identity lookup happens outside the file-writer lock. Target IDs are separate (`kviklet.target_user_id`, `role_id`, etc.). Failed authentication omits an unverified actor. Events without an identified actor omit `user`.

`source.ip`, when available for HTTP activity, is the socket peer captured before forwarded-header processing. It is **not** the claimed original client from `Forwarded` or `X-Forwarded-For`. With a reverse proxy this normally identifies that proxy. Database-proxy activity is explicitly marked by `kviklet.execution.channel: "database_proxy"`, with a generated session ID and protocol under `kviklet.proxy`; the ID is unrelated to the proxy username or password.

| Activity | Actions/details |
| --- | --- |
| Requests and reviews | `request.created`, `request.edited`, `request.reason_changed`, `request.closed`, `review.approve`, `review.reject`, `review.request_change`, `comment.added`; request and connection IDs and access reason, no comment text |
| SQL, downloads, dumps | `execution.attempted`, `execution.completed`; statement, mode, channel, duration and result metadata when available |
| Dry runs | `execution.completed` only; success/failure, `mode: "dry_run"`, channel and result metadata; statement at level 3 |
| Explain | `execution.explain.completed`; success/failure, user, request, connection, access reason and channel, with `mode: "explain"`; no SQL or query-plan output |
| Database proxies | Attempts with `database_proxy` channel, session correlation and protocol; `proxy.session_created` / `proxy.session_ended` |
| Kubernetes commands | Attempts and actual completion callback, including background completion, exit code and timeout; output excluded |
| Authentication | `authentication.login` success/failure for password, LDAP, OIDC and SAML; `authentication.logout`; API-key authentication failures |
| Identity and authorization | User creation, activation/deactivation, password-change marker, role assignment/removal including identity-provider sync, role creation/change/deletion with permission diffs, API-key creation/revocation, permission denials |
| Security settings | Connection creation/deletion and security-setting changes, credential-change marker, proxy-enabled changes, role-sync configuration/mapping changes, event-stream settings changes |

At Level 3, single-execution database request creation includes the submitted SQL in `kviklet.execution.statement`. Request edit records include the new saved SQL, title and reason after the request is updated; their stored audit event IDs are retained. The statement uses the same 64 KiB UTF-8 limit as executed SQL. Level 2 omits this field, and Level 1 omits these routine request events. Temporary-access and dump request creation/edit events do not include submitted SQL.

Request, review and execution records share `kviklet.connection`, containing `id`, `name` (display name) and `type`. Database connections also include `database_type`, `hostname`, `port` and `database_name` (which can be null). This includes Explain, Dry Run and database-proxy execution attempts. The fields snapshot the connection available when the event is captured, without an additional lookup. Request creation uses `connection.id` instead of the previous standalone `connection_id`. Credentials and additional connection options remain excluded.

Role creation, change and deletion records always include `kviklet.role_id`, `kviklet.before.name` and `kviklet.after.name`. Creation uses a null previous name; deletion uses a null new name. Permission-only edits include the unchanged name in both fields. Renames include the previous and new names alongside the existing `name_changed` flag and permission differences. Role descriptions remain excluded; description-only edits and unchanged saves do not produce events.

Coverage is limited to activity Kviklet observes. Direct database administration or changes outside the application are not captured. Proxy statements do not have completion events or result metadata because Kviklet does not interpret server results. Their `event.outcome` is `unknown`, never an inferred success. Proxy session events describe the approved access session, not each client TCP connection.

Explain and Dry Run each produce one outcome record after the database call returns or throws, with `success` when no database error is returned and `failure` on a database error or exception from that call. Rejections before the database call produce no Explain or Dry Run record. Neither activity emits a streamed attempt record. Explain does not create a stored execution event or consume an execution allowance. Dry Run retains its existing stored audit entry and emits `execution.completed` with `kviklet.execution.mode: "dry_run"`. Both activities are included at levels 2 and 3 and omitted at level 1. Permission denials handled inside the WebSocket message handler are not streamed; changing Explain enablement does not produce a connection-setting event.

Database connection creation includes `hostname`, `port`, `database_name` (which can be null), `database_type`, and `protocol` in `kviklet.security`. Changes to any of these fields produce `connection.security_changed`, with the previous and new values in `kviklet.before` and `kviklet.after` alongside the existing security settings. These events are included at all three logging levels. Unchanged values do not produce a change event. Display name, description, category, explain enablement, and additional JDBC options are not included in this security snapshot; credential changes remain a boolean marker without credential values.

Browser/API SQL metadata includes the rows returned by Kviklet's executor, column count, affected rows, dump byte count, numeric error codes and elapsed duration when available. Counts reflect executor behavior and configured result limits; they are not an estimate of rows that the database would have returned without those limits. Empty result lists produce unknown outcomes.

At Levels 2 and 3, the requester's access reason (the request description) is included as `kviklet.request.reason` on creation, reviews, request edits/closure, and execution attempts/completions, including database-proxy attempts. It reflects the request description captured when that event is prepared. A changed reason produces `request.reason_changed` with the new value after a successful transaction commit, including when cleared to an empty string. Saving an unchanged reason does not produce that event. A missing description omits the reason fields. Reasons are bounded to 64 KiB of valid UTF-8. Longer text is shortened without truncation metadata; the original request in Kviklet is unchanged.

The stream excludes stored rows, returned values, column names, passwords, API-key values/hashes, proxy credentials, license contents, connection strings, raw exception messages, review/comment text, other object descriptions, and Kubernetes stdout/stderr. Access reasons are included at Levels 2 and 3; statements and shell commands are included at Level 3. These text fields can themselves contain secrets or personal information. Level 2 omits the dedicated statement/command fields but does not redact SQL pasted into an access reason. Secure the log files and downstream SIEM appropriately. Statements are bounded to 64 KiB of valid UTF-8 and longer text is shortened without truncation metadata. Text preparation uses bounded temporary allocations even for very large inputs. JSON escaping preserves embedded newlines within a single physical line. The overall record limit is 1 MiB, allowing a full reason and statement even when JSON escaping expands their contents. An event exceeding that limit is dropped and counted. Large permission sets can therefore cause a dropped record; no silent partial permission diff is emitted.

At Levels 2 and 3, request creation, edits/closure, reviews/approvals, and execution records also include `kviklet.request.title` when the title is nonblank. This includes database proxies, Explain, Dry Run, downloads, dumps and Kubernetes executions. The title is included even when the access reason is absent and reflects the request title at event capture time.

A completed browser query, for example, has this shape (illustrative IDs):

```json
{
  "@timestamp": "2026-10-01T12:00:00.000Z",
  "ecs": {"version": "8.11.0"},
  "service": {"name": "Kviklet", "version": "dev", "ephemeral_id": "process-uuid"},
  "event": {
    "id": "record-uuid", "kind": "event", "dataset": "kviklet.audit",
    "category": ["database"], "type": ["end"],
    "action": "execution.completed", "outcome": "success"
  },
  "user": {"id": "actor-id", "name": "Dan Nguyen", "email": "dan@example.com", "roles": ["Default", "Requester"]},
  "source": {"ip": "127.0.0.1"},
  "kviklet": {
    "schema_version": "1.0.0", "request_id": "request-id", "audit_event_id": "audit-id",
    "request": {"title": "Investigate customer access", "reason": "Investigate ticket SEC-42"},
    "connection": {"id": "connection-id", "type": "DATASOURCE", "name": "Production", "database_type": "POSTGRESQL", "hostname": "db.internal", "port": 5432, "database_name": "sales"},
    "execution": {"channel": "web", "mode": "query", "statement": "SELECT id FROM users"},
    "duration_ms": 42,
    "results": [{"type": "query", "rows_returned": 17, "column_count": 1}]
  }
}
```

## Deployment and collection

Docker images prepare `/var/log/kviklet/events` for the unprivileged `nginx` user. Mount a persistent volume there before enabling streaming. For an existing host directory, set ownership for the container UID and mode 750 (or 700). The directory owner alone may write. Changing the UI path does not create a container mount.

The Helm chart accepts:

```yaml
eventStreaming:
  existingClaim: kviklet-event-logs
  mountPath: /var/log/kviklet/events
```

Provision and set permissions on the PVC separately. The chart keeps one replica and uses `Recreate` when this claim is configured to avoid overlapping writers during rollout. Configure the same directory in settings. Do not share a directory among replicas. Give your collector read access and persist its offset/registry state across restarts.

[filebeat.yml](filebeat.yml) is a local ingestion example using Filebeat 8.17's [filestream NDJSON parser](https://www.elastic.co/guide/en/beats/filebeat/8.17/filebeat-input-filestream.html). It tails both active and rotated files, parses JSON, and outputs to the console for validation. In production configure your supported SIEM output, TLS/authentication and persistent collector registry; add deployment/customer identity at the collector. Do not ingest both the source and copies of the same files unless your pipeline deduplicates by `event.id`. Do not use copy-and-truncate rotation against the active file: Kviklet owns rotation.

If copying files instead of tailing, copy completed archives and keep track of processed filenames and record IDs. The collector must drain records before retention removes them. The file lock, bounded records and restrictive permissions improve operational safety; these local files are not tamper-evident or immutable. For stronger guarantees, send them promptly to a separately controlled destination with its own retention and access controls.
