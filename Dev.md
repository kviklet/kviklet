# Dev Setup

## Run the app in containers:

```
docker-compose up -d kviklet kviklet-postgres mysql
```

- Need to rebuild to see changes:
  In the `docker-compose.yml` file, update the kviklet service to use the build context:

```
kviklet:
   # image: ghcr.io/kviklet/kviklet:main
   build: .
```

Then rebuild and restart the kviklet container:

```
docker compose build --no-cache kviklet
docker-compose up -d kviklet
```

### Steps to create new request

1. Go to localhost and login with default Admin user (specified in docker compose environment)
2. Create new user via the `Settings`
3. Create new database connection
   Note: The Hostname should be the mysql container's IP
   Get container IP: docker inspect -f '{{range .NetworkSettings.Networks}}{{.IPAddress}}{{end}}' <container_id>

## Run the app locally (MacOS):

### Prerequisites:

- MySQL
- PostgreSQL
- Node.js
- Java

### Steps:

1. Start a mysql server

```
mysql.server start --port=3307
```

2. Start a PostgreSQL that serves kviklet on port 5432

```
brew services start postgresql@16
```

3. Create an `application-local.properties` file in the `src/main/resources` directory with the appropriate configuration settings. For example:

```
spring.datasource.url=jdbc:postgresql://127.0.0.1:5432/kviklet
spring.datasource.username=postgres
spring.datasource.password=postgres
```

4. Create a PostgreSQL database and set the database owner to the initial user specified in `application-local.properties`.

5. Start the dev frontend server:

```
   cd frontend/
   npm install
   npm run start
```

6. Start the dev backend server:

```
   cd backend/
   ./gradlew bootRun --args='--spring.profiles.active=local'
```

## docker compose build --no-cache kviklet

## Tests and checks

- Backend local build:

```
./gradlew build
```

- Run all tests:

```
./gradlew test

# Clear the cache and run the test
./gradlew clean test
```

- Run test class:

```
./gradlew test --tests "com.yourpackage.YourTestClass"
```

- Run ktlint:

```
ktlint
```

- Autocorrect ktlint errors run:

```
ktlint --format
```
