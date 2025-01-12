# Kviklet Helm Chart

## Quickstart

1. Create a kubernetes secret with the following values

```
apiVersion: v1
kind: Secret
metadata:
  name: kviklet-secret
  namespace: kviklet
type: Opaque
stringData:
  SPRING_DATASOURCE_USERNAME: "postgres"
  SPRING_DATASOURCE_PASSWORD: "postgres"
```

2. Run the following command to install kviklet service with default configuration.

```
helm install [RELEASE_NAME] -n kviklet --create-namespace .
```

## Configuration

Look at the values.yaml to see which configuration options are natively supported by the chart.
Any other configuration can be put into the same Secret as the database credentials with the environment variable name listed in the general README.md.

If you think some config should be supported natively by the chart and not required as a secret, please open an issue or a PR.

## Example

There is a [demo deployment on GCS](../kviklet-demo/README.md) that makes use of this base chart.
