# Kviklet Helm Chart

## Prerequisites

Kviklet requires an external PostgreSQL database (version 12+). This chart does not include an embedded database.

Supported databases:
- PostgreSQL 12+

## Quickstart

1. Prepare a PostgreSQL database with:
   - A database created (e.g., `kviklet`)
   - A user with full access to the database
   - Network access from your Kubernetes cluster

2. Create a kubernetes secret with the following values

```yaml
apiVersion: v1
kind: Secret
metadata:
  name: kviklet-secret
  namespace: kviklet
type: Opaque
stringData:
  SPRING_DATASOURCE_URL: "jdbc:postgresql://your-db-host:5432/kviklet"
  SPRING_DATASOURCE_USERNAME: "your-db-username"
  SPRING_DATASOURCE_PASSWORD: "your-db-password"
  INITIAL_USER_EMAIL: "admin@example.com"
  INITIAL_USER_PASSWORD: "secure-password"
```

3. Run the following command to install kviklet service with default configuration.

```bash
helm install [RELEASE_NAME] -n kviklet --create-namespace .
```

## Configuration

Look at the values.yaml to see which configuration options are natively supported by the chart.
Any other configuration can be put into the same Secret as the database credentials with the environment variable name listed in the general README.md.

If you think some config should be supported natively by the chart and not required as a secret, please open an issue or a PR.

## Database access proxies (experimental)

Kviklet can proxy PostgreSQL and MySQL/MariaDB connections so that approved sessions can be used from a regular database client. The listeners are enabled by default inside the pod (`config.proxy.postgres` on 5432, `config.proxy.mysql` on 3306) but are not exposed outside the pod unless you enable the dedicated proxy service:

```yaml
proxyService:
  enabled: true
  type: LoadBalancer # database clients speak raw TCP, an HTTP ingress cannot carry this traffic
```

To disable a listener entirely, set e.g. `config.proxy.mysql.enabled: false`.

### TLS

Without a certificate the proxies accept unencrypted connections only. To serve TLS, add the certificate to the same secret as the other environment variables:

```yaml
stringData:
  PROXY_TLS_CERTIFICATE_SOURCE: "env"
  PROXY_TLS_CERTIFICATE_CERT: |
    -----BEGIN CERTIFICATE-----
    ...
  PROXY_TLS_CERTIFICATE_KEY: |
    -----BEGIN PRIVATE KEY-----
    ...
```

The certificate must match the DNS name under which database clients reach the proxy service, which is usually different from the hostname of the web UI.

## Example

There is a [demo deployment on GCS](../kviklet-demo/README.md) that makes use of this base chart.
