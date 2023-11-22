# Kviklet

# Under Construction!

This project is not yet functional and is currently being built!

Read further to understand what we are trying to accomplish.

## 🔐 The developer-centric DevSecOps solution for production data access

Kviklet aims to provide secure access to production databases without impairing developer productivity.

Utilizing the Four-Eyes Principle and a high level of configurability, Kviklet allows a Pull Request-like Review and Approval flow for individual Database statements or full Database sessions.

Kviklet is a self hosted solution, this is a deliberate design decision as you shouldn't have to expose your databases to any external party. However we try to make it a simple as possible to get set up.

Initially we will focus on PostgreSQL and MySQL support.

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

With all this set you can run `jaschaKviklet/Kviklet:latest` don't forget to expose port 80.

An example docker run could looks like this:

```
docker run \
-e SPRING_DATASOURCE_PASSWORD=postgres \
-e SPRING_DATASOURCE_USERNAME=postgres \
-e SPRING_DATASOURCE_URL=jdbc:postgresql://localhost:5433/Kviklet \
-e INITIAL_USER_EMAIL=admin@example.com \
-e INITIAL_USER_PASSWORD=someverysecurepassword \
--network host \
jaschaKviklet/Kviklet:latest
```
