# Kviklet

[Kviklet.dev](https://kviklet.dev) | [Release Notes](https://github.com/kviklet/kviklet/releases)

<p align="center">
<img src="https://github.com/kviklet/kviklet/raw/main/images/ExecutionRequest.png" height="500px">
</p>

Secure access to production databases without impairing developer productivity.

Kviklet utilizes the **Four-Eyes Principle** and a high level of configurability, to allow a **Pull Request-like Review and Approval** flow for individual SQL statements or Database sessions. This allows engineering teams to self regulate on who gets access to what data and when.

Kviklet is a self hosted docker container, that provides you with a Single Page Web app that you can login to create your SQL requests or approve the ones of others.

We currently only support Postgres and MySQL but more is coming. If you have a specific DB required or other fea

## Features

Kviklet ships with a variety of features that an engineering team needs to manage their production database access in a **simple but secure** manner:

- **SSO (Google)**: Log into Kviklet without the need for a username or password. No more shared credentials for DB access.
- **Review/Approval Flow**: Leave Comments and Suggestions on other developers data requests.
- **Temporary Access (1h)**: Execute any statement on a db for 1h after having been approved
- **Single Query**: Execute a singular statement. Allows the reviewer to review your query before execution.
- **Auditlog**: Singular plane that logs all executed statements with Author, reason for execution etc.
- **RBAC**: Configure which team has access to which database/table to as fine of a granularity as the DB Engine allows.
- **Postgres Proxy**: Start a proxy server to use the DB Client of your choice, but everything will be stored in the Kviklet Auditlog.
- And more...

## Setup

Kviklet runs as a standalone docker container.
To start you can find the available verions under [Releases](https://github.com/kviklet/kviklet/releases) you can chose your desired version tag. The latest one currently is `ghcr.io/kviklet/kviklet:0.1.1`, you can also use `:main` but it might happen every now and then that we accidentally merge something buggy alhtough we try to avoid that. Also make sure to check back every now and then since we regularly release new versions with bugfixes, and new features.

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
KVIKLET_IDENTITY_PROVIDER_CLIENT_ID
KVIKLET_IDENTITY_PROVIDER_CLIENT_SECRET
KVIKLET_IDENTITY_PROVIDER_TYPE=google
```

The google client id and secret you can easily get by following google instructions here:
https://developers.google.com/identity/gsi/web/guides/get-google-api-clientid

For valid redirect URIs, you should configure: http://[kviklet_host]/api/login/oauth2/code/google
For Allowed Origins, simply your hosted kviklet url.

After setting those environment variables everyone in your organization can login with the sign in with google button. But they wont have any permissions by default, you will have to assign them a role after they log in once.

### Keycloak SSO

If you want to setup SSO with Keycloak instead you need to set these 4 environment variables:

```
KVIKLET_IDENTITY_PROVIDER_CLIENT_ID
KVIKLET_IDENTITY_PROVIDER_CLIENT_SECRET
KVIKLET_IDENTITY_PROVIDER_TYPE=keycloak
KVIKLET_IDENTITY_PROVIDER_ISSUER_URI=http://[host]:[port]/realms/[realm]
```

You get the client id and secret when you create an application in keycloack.
For valid redirect URIs, you should configure: http://[kviklet_host]/api/login/oauth2/code/google
For Allowed Origins, simply your hosted kviklet url.

After setting those environment variables the login page should show a Login with Keycloak button that redirects to your keycloak instance. We do currently not support role sync yet so you will have to manage roles directly in kviklet manually for now.

### Proxy (Beta), Postgres only

If you create requests for temporary access. You can instead of using the web interface to run queries also enable a proxy and use the DB client of your choice.
For this the container uses ports 5438-6000 so you need to expose those.
The user can then create a temp access request, and click "Start Proxy" once it's been approved. They will get a port and a user + temporary password. With this they can login to the database. Kviklet validates the temp user and password and proxies all requests to the underlying user on the database. Any executed statements are logged in the auditlog as if they were run via the web interface.

<p align="center">
<img src="https://github.com/kviklet/kviklet/raw/main/images/Proxy.png" height="400px">
</p>

## Configuration

### Connections

After starting Kviklet you first have to configure a database connection. Go to Settings -> Databases -> Add Connection.

<p align="center">
<img src="https://github.com/kviklet/kviklet/raw/main/images/AddConnectionForm.png" height="400px">
</p>

After creating a connection, you can configure how many reviews are required for running queries via this connection.

### Roles

Kviklet ships with 2 default roles, Admins and Developers.

- Admins have the permission to create and edit connections, as well as adding new Users and setting their permissions.
- Developers can create Requests as well as approve and comment on them and ofcourse execute the actual statements.

You can completely customize Roles and e.g. give a role only access to a specific connection or a group of db connections.
