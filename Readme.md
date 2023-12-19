# Kviklet

Secure access to production databases without impairing developer productivity.

Kviklet utilizes the Four-Eyes Principle and a high level of configurability, to allow a Pull Request-like Review and Approval flow for individual SQL statements or Database sessions. This allows engineering teams to self regulate on who gets access to what data and when.

Kviklet is a self hosted docker container, that provides you with a Single Page Web app that you can login to to create your SQL requests or approve the ones of others.

We currently only support Postgres but more is coming.

### Under Construction!

This project is not yet fully functional and is currently being built! If you are interested in the featureset feel free to reach out we are happy to cooperate and prioritize your feature requests.

See our website for more details on who is behind this project: https://kviklet.dev

## Setup

We currently publish only a latest tag of the container on every commit. Use this at your own risk. Versioning will come in the future.

### DB Setup

Kviklet needs it's own database (or at least schema) to save metadata about queries, connections, approvals, etc.
In theory you can setup a MySQL or Postgres DB for this purpose, we internally only use postgres though, so this is the recommended path.

When starting the container you then need to set these three environment variables:

```
SPRING_DATASOURCE_PASSWORD = password
SPRING_DATASOURCE_USERNAME = username
SPRING_DATASOURCE_URL = jdbc:postgresql://[url]:[port]/[database]?currentSchema=[schema]
```

### Initial User

You will need an intial admin user for configuration purposes. For this set the 2 env variables:
`INITIAL_USER_EMAIL` and `INITIAL_USER_PASSWORD` so that you can login into the web interface. You can change the password afterwards via the UI.  
Example:

```
INITIAL_USER_EMAIL=admin@example.com
INITIAL_USER_PASSWORD=someverysecurepassword
```

We publish our containers do the github packages for now, so with all this set you can run `ghcr.io/kviklet/kviklet:main` don't forget to expose port 80.

An example docker run could looks like this:

```
docker run \
-e SPRING_DATASOURCE_PASSWORD=postgres \
-e SPRING_DATASOURCE_USERNAME=postgres \
-e SPRING_DATASOURCE_URL=jdbc:postgresql://localhost:5433/Kviklet \
-e INITIAL_USER_EMAIL=admin@example.com \
-e INITIAL_USER_PASSWORD=someverysecurepassword \
--network host \
ghcr.io/kviklet/kviklet:main
```

### Google SSO

If you want to setup SSO for your Kviklet instance (which makes a lot of sense since otherwise you have to manage passwords again).
You need to setup these 3 environment variables:

```
kviklet_identity-provider_client-id
kviklet_identity-provider_client-secret
kviklet_identity-provider_type=google
```

The google client id and secret you can easily get by following google instructions here:
https://developers.google.com/identity/gsi/web/guides/get-google-api-clientid

After setting those environment variables everyone in your organization can login with the sign in with google button. But they wont have any permissions by default, you will have to assign them a role after they log in once.
