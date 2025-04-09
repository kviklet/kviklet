# Kviklet

[Kviklet.dev](https://kviklet.dev) | [Release Notes](https://github.com/kviklet/kviklet/releases)

Secure access to production environments without impairing developer productivity.

![Kviklet](images/ExecutedRequest_light.png#gh-light-mode-only)
![Kviklet](images/ExecutedRequest_dark.png#gh-dark-mode-only)

Kviklet (pronounced Quick-let) embraces the **Four-Eyes Principle** and a high level of configurability to allow a **Pull Request-like Review and Approval** flow for individual SQL statements or Database sessions. This allows engineering teams to self regulate on who gets access to what data and when, allowing organizations to stay secure and compliant while embracing modern, empowering and truly "DevOps" workflows.

Kviklet is a self hosted docker container, that provides you with a Single Page Web app. Login to create SQL requests or approve the ones of others.

We currently support **Postgres**, **MySQL**, **MS SQL Server** and **MongoDB**.

## Features

Kviklet ships with a variety of features that an engineering team needs to manage their production database access in a **simple but secure** manner:

- **SSO (Google)**: Log into Kviklet without the need for a username or password. No more shared credentials for DB access.
- **LDAP Support**: Log into Kviklet with your LDAP credentials.
- **Review/Approval Flow**: Leave Comments and Suggestions on other developers data requests.
- **Temporary Access (1h)**: Execute any statement on a db for 1h after having been approved
- **Single Query**: Execute a singular statement. Allows the reviewer to review your query before execution.
- **Auditlog**: Singular plane that logs all executed statements with Author, reason for execution etc.
- **RBAC**: Configure which team has access to which database/table to as fine of a granularity as the DB Engine allows.
- **Postgres Proxy**: Start a proxy server to use the DB Client of your choice, but everything will be stored in the Kviklet Auditlog.
- **Kubernetes Exec**: Execute a statement on a pod in your kubernetes cluster. (Currently only supports Execution of a single command no live session yet)

## Feature by Database/Connection Type

Most features are available for all databases (SSO, LDAP, RBAC, Review/Approval Flow, Auditlog, etc.). But some features are restricted, either because it simply hasn't been built yet or because it makes no sense for that specific purpose. The following table shows which features are available for which database type:

| Database   | Statement Review | Temporary Access | Proxy(Beta) | Explain Plan |
| ---------- | ---------------- | ---------------- | ----------- | ------------ |
| Postgres   | &check;          | &check;          | &check;     | &check;      |
| MySQL      | &check;          | &check;          | &cross;     | &check;      |
| MariaDB    | &check;          | &check;          | &cross;     | &check;      |
| SQL Server | &check;          | &check;          | &cross;     | &check;      |
| MongoDB    | &check;          | &check;          | &cross;     | &cross;      |
| Kubernetes | &check;          | &cross;          | &cross;     | &cross;      |

## Setup

Kviklet ships as a simple docker container.
You can find the available versions under [Releases](https://github.com/kviklet/kviklet/releases). We recommend regularly updating the version you are using as we continue to build new features.  
The latest one currently is `ghcr.io/kviklet/kviklet:0.5.1`, you can also use `:main` but it might happen every now and then that we accidentally merge something buggy. Though we try to avoid that.

### Quick Start

If you just want to try out how it works:

1. Here is a minimal docker-compose.yaml:
   <details>
   <summary> Click to expand compose content </summary>

   ```
   services:
     postgres:
       image: postgres:16
       restart: always
       environment:
         POSTGRES_USER: postgres
         POSTGRES_PASSWORD: postgres
         POSTGRES_DB: postgres
       ports:
         - "5432:5432"
       volumes:
         - ./postgres-data:/var/lib/postgresql/data
   #      - ./sample_data.sql:/docker-entrypoint-initdb.d/init.sql

     kviklet-postgres:
       image: postgres:16
       restart: always
       environment:
         POSTGRES_USER: postgres
         POSTGRES_PASSWORD: postgres
         POSTGRES_DB: kviklet
       ports:
         - "5433:5432"
       volumes:
         - ./kviklet-postgres-data:/var/lib/postgresql/data

     kviklet:
       image: ghcr.io/kviklet/kviklet:main
       ports:
         - "80:8080"
       environment:
         - SPRING_DATASOURCE_URL=jdbc:postgresql://kviklet-postgres:5432/kviklet
         - SPRING_DATASOURCE_USERNAME=postgres
         - SPRING_DATASOURCE_PASSWORD=postgres
         - INITIAL_USER_EMAIL=admin@admin.com
         - INITIAL_USER_PASSWORD=admin
       depends_on:
         - kviklet-postgres
   ```

   </details>

2. Run the `docker-compose.yml` via `docker-compose up -d`. Kviklet will spin up on port 80, go to `localhost` and play around. The admin login is admin@admin.com with `admin` as password.

3. The docker-compose contains an extra postgres database for which you can setup a connection in Kviklet. To make this database contain some data, uncomment this line:

   ```
         - ./sample_data.sql:/docker-entrypoint-initdb.d/init.sql
   ```

   And create a sample_data.sql file:

   <details>
   <summary> Click to expand sample_data.sql content </summary>

   ```sql
   CREATE TABLE Locations (
       Name VARCHAR(100) NOT NULL,
       Address VARCHAR(255) NOT NULL,
       City VARCHAR(100) NOT NULL,
       Country VARCHAR(100) NOT NULL,
       PostalCode VARCHAR(20) NOT NULL
   );

   alter table public.Locations
       owner to postgres;

   INSERT INTO public.Locations (Name, Address, City, Country, PostalCode) VALUES
   ('Central Park', '59th to 110th St', 'New York', 'USA', '10022'),
   ('Eiffel Tower', 'Champ de Mars, 5 Avenue Anatole', 'Paris', 'France', '75007'),
   ('Colosseum', 'Piazza del Colosseo, 1', 'Rome', 'Italy', '00184'),
   ('Sydney Opera House', 'Bennelong Point', 'Sydney', 'Australia', '2000'),
   ('Great Wall of China', 'Huairou District', 'Beijing', 'China', '101405');
   ```

   </details>

### DB Setup

Kviklet needs it's own postgres database (or at least schema) to save metadata about queries, connections, approvals, etc.
You can find their official image here: https://hub.docker.com/_/postgres, or use a cloud hosted version by your cloud provider of choice.

When starting the kviklet container you will then need to set these three environment variables accordingly:

```
SPRING_DATASOURCE_PASSWORD = password
SPRING_DATASOURCE_USERNAME = username
SPRING_DATASOURCE_URL = jdbc:postgresql://[host]:[port]/[database]?currentSchema=[schema]
```

#### Alternative Authentication methods

- **IAM Auth:**
  It is possible to use AWS IAM Auth for the database connection, in which case you simply omit the password and just set the username.
  You also have to set the env var:

  ```
  SPRING_DATASOURCE_IAMAUTH=true
  ```

  Kviklet will load credentials from the usual places (env vars, instance roles, etc.) and generate a token for the connection.

- **Certificates:**
  You can also use certificates for the db connection, see [here](examples/certificates) for an example.

### Initial User

You will need an intial admin user for configuration purposes. For this set the 2 env variables:
`INITIAL_USER_EMAIL` and `INITIAL_USER_PASSWORD` so that you can login into the web interface. You can change the password afterwards via the UI.  
Example:

```
INITIAL_USER_EMAIL=admin@example.com
INITIAL_USER_PASSWORD=someverysecurepassword
```

We publish our containers do the github packages for now, so with all this set you can run `ghcr.io/kviklet/kviklet:main` don't forget to map port `8080` which is the default port Kviklet spins up on.

An example docker run could looks like this:

```
docker run \
-e SPRING_DATASOURCE_PASSWORD=postgres \
-e SPRING_DATASOURCE_USERNAME=postgres \
-e SPRING_DATASOURCE_URL=jdbc:postgresql://localhost:5432/Kviklet \
-e INITIAL_USER_EMAIL=admin@example.com \
-e INITIAL_USER_PASSWORD=someverysecurepassword \
--network host \
ghcr.io/kviklet/kviklet:main
```

### SSO

#### Google

If you want to setup SSO for your Kviklet instance (which makes a lot of sense since otherwise you have to manage passwords again).
You need to setup these 3 environment variables:

```
KVIKLET_IDENTITYPROVIDER_CLIENTID
KVIKLET_IDENTITYPROVIDER_CLIENTSECRET
KVIKLET_IDENTITYPROVIDER_TYPE=google
```

The google client id and secret you can easily get by following google instructions here:
https://developers.google.com/identity/gsi/web/guides/get-google-api-clientid

For valid redirect URIs, you should configure: https://[kviklet_host]/api/login/oauth2/code/google
For Allowed Origins, simply your hosted kviklet url.

After setting those environment variables everyone in your organization can login with the sign in with google button. But they wont have any permissions by default, you will have to assign them a role after they log in once.

#### Keycloak

If you want to setup SSO with Keycloak instead you need to set these 4 environment variables:

```
KVIKLET_IDENTITYPROVIDER_CLIENTID
KVIKLET_IDENTITYPROVIDER_CLIENTSECRET
KVIKLET_IDENTITYPROVIDER_TYPE=keycloak
KVIKLET_IDENTITYPROVIDER_ISSUERURI=http://[host]:[port]/realms/[realm]
```

You get the client id and secret when you create an application in keycloack.
For valid redirect URIs, you should configure: https://[kviklet_host]/api/login/oauth2/code/keycloak
For Allowed Origins, simply your hosted kviklet url.

After setting those environment variables the login page should show a Login with Keycloak button that redirects to your keycloak instance. We do currently not support role sync yet so you will have to manage roles directly in kviklet manually for now.

#### Other OIDC providers

Other OIDC providers should work similarly to Keycloak. Note that the `redirect URI` will change depending on they type you choose, so if you choose `gitlab` it will be `https://[kviklet_host]/api/login/oauth2/code/gitlab`.
If you run into issues feel free to create an issue, we have not tried every single OIDC provider out there (yet) and there might be slight differences in the implementation that might require updates on Kviklets side.

### LDAP

Kviklet supports LDAP authentication. To enable and configure LDAP, you can override the following environment variables:

```
LDAP_ENABLED=true
LDAP_URL=ldap://your-ldap-server:389
LDAP_BASE=dc=your,dc=domain,dc=com
LDAP_PRINCIPAL=cn=admin,dc=your,dc=domain,dc=com
LDAP_PASSWORD=your-admin-password
LDAP_UNIQUE_IDENTIFIER_ATTRIBUTE=uid
LDAP_EMAIL_ATTRIBUTE=mail
LDAP_FULL_NAME_ATTRIBUTE=cn
LDAP_USER_OU=people
LDAP_SEARCH_BASE=ou=people
```

Here's what each setting means:

- `LDAP_ENABLED`: Set to `true` to enable LDAP authentication.
- `LDAP_URL`: The URL of your LDAP server.
- `LDAP_BASE`: The base DN for LDAP searches.
- `LDAP_PRINCIPAL`: The DN of the admin user for binding to the LDAP server.
- `LDAP_PASSWORD`: The password for the admin user.
- `LDAP_UNIQUE_IDENTIFIER_ATTRIBUTE`: The LDAP attribute used as the unique identifier for users (default: "uid").
- `LDAP_EMAIL_ATTRIBUTE`: The LDAP attribute that contains the user's email address (default: "mail").
- `LDAP_FULL_NAME_ATTRIBUTE`: The LDAP attribute that contains the user's full name (default: "cn").
- `LDAP_USER_OU`: The Organizational Unit (OU) where user accounts are stored (default: "people").
- `LDAP_SEARCH_BASE`: Allows to override the base DN for user searches (default: "ou=people"). If you use FreeIPA you might need to set this to e.g. `cn=users`. If set LDAP_USER_OU is ignored.

You can customize these attributes to match your LDAP schema. After configuring LDAP, users will be able to log in using their LDAP credentials. The first time an LDAP user logs in, a corresponding user account will be created in Kviklet with default permissions. An admin will need to assign appropriate roles to these users after their first login.

## Configuration

### Connections

After starting Kviklet you first have to configure a database connection. Go to Settings -> Databases -> Add Connection.

![Add Connection](images/CreateConnection_light.png#gh-light-mode-only)
![Add Connection](images/CreateConnection_dark.png#gh-dark-mode-only)

Here you can configure how many reviews are required to run Requests on this connection. You can also configure how often a request can be run. The default is 1 and we recommend to stick to this for most use cases. As a special config, setting this to 0 any request on the connection can be run an infinite amount of times.

#### AWS IAM AUTH

Kviklet supports using IAM Auth for Postgres and MySQL Database Connections for this choose IAM Auth when creating a new connection.

![IAM Auth](images/CreateConnectionIAM_light.png#gh-light-mode-only)
![IAM Auth](images/CreateConnectionIAM_dark.png#gh-dark-mode-only)

This will remove the option to set a password and instead user AWS credentials to connect to the database.

Kviklet uses AWS's `DefaultCredentialsProvider` to find credentials and generate the token for the connection. This means all typical places should work (env vars or associated instance roles) exact order is documented here: https://sdk.amazonaws.com/java/api/latest/software/amazon/awssdk/auth/credentials/DefaultCredentialsProvider.html

The AWS region to use during token generation is inferred from your connection URL so there is no options to set it.

To learn how to setup IAM Auth for your database follow the official AWS documentation: https://docs.aws.amazon.com/AmazonRDS/latest/UserGuide/UsingWithRDS.IAMDBAuth.html
The main two points are:

- Create a DB user with the IAM auth option and correct permissions
- Create an IAM policy that allows the AWS entity to generate tokens for this user

### Roles

Kviklet ships with 3 roles, Default, Admins and Developers.

- The default role provides Read access to all connections, and Requests. This role is assigned to every user and cannot be removed. You can however alter the permissions of this role however you like.
- Admins have the permission to create and edit connections, as well as adding new Users and setting their permissions.
- Developers can create Requests as well as approve and comment on them and of course execute the actual statements.

You can customize Roles and e.g. give a role only access to a specific connection or a group of DB connections.
This is useful e.g. if you have different teams with different databases and want to controll access to those more granularly.

#### Creating a new Role

Creating a new role works as follows. Go to Settings -> Roles -> Add Role.

![Add Role](images/CreateRole_light.png#gh-light-mode-only)
![Add Role](images/CreateRole_dark.png#gh-dark-mode-only)

The default settings are not as relevant for most roles and you can just give User Read and RoleView Access and leave it at that.
More interesting is the adding of individual permissions for Connections. Here you first add a selector to select specific connections. This can either be a specific id or you use wildcards with `*` to match multiple connections. E.g. if you want to have a role that has access to all dev databases (in case you also manage acces to those with kviklet) you'd use a selector like `dev-*` and ensure the ids of the connections are set correctly.

You can of course also make up a system that you use for your different teams inside of your organization.

### Notifications

You can configure Kviklet to send notifications to a channel in Slack or Teams. This is useful to notify your team about new requests that need to be reviewed. You can configure this in Settings -> General -> Notification Settings.

#### Slack

To configure Slack notifications you need to create a Slack App and enable webhooks for it. You can follow the instructions here: https://api.slack.com/messaging/webhooks

#### Teams

To configure Teams notifications you need to add a Webhook connector to your channel. The official microsoft docs on that are here: https://docs.microsoft.com/en-us/microsoftteams/platform/webhooks-and-connectors/how-to/add-incoming-webhook

To enable the notifications, simply set the Webhook URL in the Kviklet Notification Settings and click save.

Currently there are notifications for:

- New Requests, that need approvals
- New approvals on requests

## Encryption

If you don't want the credentials to be stored in cleartext in the DB, it is recommended that you enable database encryption on the Kviklet postgres DB itself. For most hosted providers this is a simple checkbox to click.  
Nonetheless, if the Kviklet database is somehow compromised, this is a huge security risk. As it contains the database crendetials for potentially all your production datastores. So you can enable encryption of the credentials at rest.

To do this simply set the two environment variables.

```
ENCRYPTION_ENABLED=true
ENCRYPTION_KEY_CURRENT=some-secret
```

Kviklet will encrypt all your existing credentials on startup, and use the secret for future connections that you create.

### Key Rotation

If you want to rotate the key you can simply add another variable for the previous key and change the current one:

```
ENCRYPTION_KEY_PREVIOUS=some-secret
ENCRYPTION_KEY_CURRENT=another-secret
```

Kviklet will re-encrypt all connections on startup, so that you can then restart the contaienr with the previous key removed.

## Experimental Features

There are currently two experimental Features. That were built mostly on community feedback. Feel free to try these out and leave any input that you might have. We hope to develop into this further in the future and make it work well with the core approval flow.

### Kubernetes Exec

If you want to use the Kubernetes Exec feature you have to create a separate kubernetes connection. Kviklet will use the user of the deployed pod to execute the command. So make sure that the user has the necessary permissions to execute commands on the pods that you want to access.

Kviklet also uses /bin/sh to execute the command, so you will need to make sure your pods have a shell or at least a symlink in /bin/sh. If this bothers you feel free to open an issue, we can potentially make this configurable or find another solution.

Kubernetes commands only wait for 5 seconds for output if the command takes longer than that Kviklet will wait for up to an hour before timing out the command. This is a a provisional solution, we are looking into websockets to make this more responsive and potentially enable terminal sessions.

### Proxy, Postgres only

If you create requests for temporary access, you can instead of using the web interface to run queries also enable a proxy and use the DB client of your choice.
For this the container uses ports 5438-6000 so you need to expose those.
The user can then create a temp access request, and click "Start Proxy" once it's been approved. They will get a port and a user + temporary password. With this they can login to the database. Kviklet validates the temp user and password and proxies all requests to the underlying user on the database. Any executed statements are logged in the auditlog as if they were run via the web interface.

![Postgres Proxy](images/PostgresProxy_light.png#gh-light-mode-only)
![Postgres Proxy](fiimages/PostgresProxy_dark.png#gh-dark-mode-only)

#### Postgres Proxy - TLS 
The proxy support TLS, currently single certificate must be configured for all connections. The implication from this is that only single domain for it can be used.
The proxy can either read the TLS certificate and key from environment variables, or from files. The table below outlines a list with them
If the `PROXY_TLS_CERTIFICATE_SOURCE` environment variable is not set, the proxy won't support TLS. If it is set to `env`, only `PROXY_TLS_CERTIFICATE_CERT` and `PROXY_TLS_CERTIFICATE_KEY` variables will be used. 
If it is set to file, the only `PROXY_TLS_CERTIFICATE_CERT_FILE` and `PROXY_TLS_CERTIFICATE_KEY_FILE` will be used.
| Environment variable            | Description                                                                                                 |
|---------------------------------|-------------------------------------------------------------------------------------------------------------|
| PROXY_TLS_CERTIFICATE_SOURCE    | Outlines what is the source for the TLS certificates. Valid values are "env" and "file". Case insensitive   |
| PROXY_TLS_CERTIFICATE_CERT      | Certificate(public key) which must be used for all connection to the proxy. Must be in pem format.          |
| PROXY_TLS_CERTIFICATE_KEY       | Private key, corresponding to the public key. Used for all connections to the proxy. Must be in pem format. |
| PROXY_TLS_CERTIFICATE_CERT_FILE | Full path to file containing PEM encoded certificate(public key)                                            |
| PROXY_TLS_CERTIFICATE_KEY_FILE  | Full path to file containing PEM encoded private key                                                        |
## Questions? Contributions?

If you have any questions, feel free to create a github issue I try to answer within a reasonable amount of time and am also happy to develop feature for your use case if it fits the general vision of the tool.
Kviklet is currently fully open-source and although I dream of making it pay my bills eventually there is currently no concrete plans on how to approach this.

If you want to contribute, feel free to fork and create PRs for small things. If you plan bigger features, I'd appreciate some discussion upfront in a github issue or similar.

You can also contact me at jascha@kviklet.dev.
