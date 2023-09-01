# OpsGate

## 🔐 Opsgate: Your Developer-Centric DevSecOps Solution

Welcome to Opsgate - We aim provide secure access to production environments while ensuring the highest standards of security. Adopting the Four Eyes principle, Opsgate allows review and approval of individual database statements. This ensures that no command is executed without a second pair of eyes, reinforcing both security and collaboration. With Opsgate you can bridge the gap between agility and safety, and empower your developers without compromising on security.

## Getting Started:

OpsGate is a self hosted solution, this is a deliberate design decision as you shouldn't have to expose your databases to any external party. However we try to make it a simple as possible to get set up:

### Kubernetes:

- We provide a preconfigured helm chart here: ...

### Anything else:

- If you do not use helm, you can use the docker image directly like this: `docker run opsgate:1.0`

### Configuration:

- When starting you should provide the environment variables `OG_ADMIN` and `OG_PASSWORD`. Don't worry you can delete this user later but it's necessary for the initial setup. (Defaults are admin, admin)

### Persistent Storage:

By default OpsGate starts with an H2 Database. H2 is an in-memory db useful to quickly get started. However to make your configuration persistent, e.g. over updates of OpsGate or when the container crashes, OpsGate needs persistent storage. For this you can use either MySQL or Postgresql if you are familar with neither, chose Postgresql.

Either way start with H2, the Database can easily be set up over the Settings page once you're running.

## Features:

Opsgate is designed to enable a full DevSecOps "You build it you run it" adoption by your team. Theoretically enabling full production access to all of engineering, the extent to which you actually allow this, is your decision though.

### Reviews and Approvals:

At the core, OpsGate allows the enforcing of the Four Eyes principle for database access, the configuration options for this are intentionally kept flexible so that you can match it to your organization:

- Our suggestion for teams with less than 50 engineers is to use a configuration that allows any read queries but require a colleagues approval for writes.

- With bigger teams you might want to require the managers approval for writes, or even for reads if your data is more sensitive (e.g. allows personal identification)

- In even bigger teams it might make sense to require approvals by your security and compliance team, etc.

### SSO and SAML:

OpsGate allows to access databases via Single-Sign-On and also supports SAML (in the enterprise version).
You can also configure users manually if you prefer that.

The fact that every user has their own account allows to connect any data access directly to an individual. No more shared passwords, if you use SSO there is no password at all.

With SAML you can even fully automate the access rights.

### Auditing

Since every action in OpsGate is linked to an individual user, there is an audit trail containing every single operation. This audit trail is available to any operational user. However note that an audit only allows you to trace an attack back to a person. It would be better to prevent an attack altogether -> so configure your reviews sensibly.

The audit log can be exported to any typical cloud file storage (s3, azure blob storage or GCS) if desired.

The Audit can also optionally be configured to copy any results of statements (actual rows or rows affected) to the same cloud bucket to have a full trace of who saw what.

### Configurable via Infrastructure as Code

We build a fully fleshed terraform provider for OpsGate, if you use it you can fully configure all Database connections and required review configurations via IAC and don't ever have to know a database password, thereby eliminating all weak links.

We highly recommend you use this way to setup OpsGate.

## Supported (Data)-Sources:

We currently support:

- Postgresql
- MySQL
- MariaDB

We will continuously add more data sources if you are waiting for something specific feel free to shoot us a message at feedback@opsgate.io.
