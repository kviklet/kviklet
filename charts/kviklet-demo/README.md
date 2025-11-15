# Kviklet Demo Deployment Helm Chart

This helm chart is used to deploy a demo installation of Kviklet onto a GKE cluster with an external PostgreSQL database.

The demo is available at [demo.kviklet.dev](https://demo.kviklet.dev). However it's gated behind Google Identity-Aware Proxy so only members of the kviklet org can access it.

If you want to access the demo, please reach out to me under jascha@kviklet.dev. You can also simply access a demo by running the container locally (see the [README](../../README.md) for instructions).

## Database Configuration

**Database:** Uses a managed PostgreSQL instance with credentials stored in GitHub Secrets (Demo environment).

Required GitHub Secrets (in "Demo" environment):
- `DB_CONNECTION_URL` - JDBC connection string
- `DB_USERNAME` - Database username
- `DB_PASSWORD` - Database password
- `INITIAL_USER_EMAIL` - Admin user email for demo
- `INITIAL_USER_PASSWORD` - Admin user password for demo

## Usage

If you want to deploy Kviklet on Kubernetes yourself, please use the base chart instead. The demo chart is only meant to be used for the demo deployment and also serves as an example for how to use it.