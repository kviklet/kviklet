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

## Example

There is a [demo deployment on GCS](../kviklet-demo/README.md) that makes use of this base chart.
