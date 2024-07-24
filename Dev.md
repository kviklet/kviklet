## Dev Setup

### Run the app in containers:

```
docker compose build --no-cache kviklet
docker-compose up -d kviklet kviklet-postgres mysql
```

#### Steps to create new request

1. Go to localhost and login with default Admin user:
   User: testUser@example.com
   Password: testPassword

2. Create new user via the Settings
3. Create new database connection
   Note: The Hostname should be the mysql container's IP
   Get container IP: docker inspect -f '{{range .NetworkSettings.Networks}}{{.IPAddress}}{{end}}' <container_id>

### Run frontend container:

```
docker build --no-cache -t frontend .
docker run --name frontend -d -p 8888:8888 frontend
```

### Run the app locally (MacOS):

#### Prerequisites:

- MySQL
- PostgreSQL
- Node.js
- Java

#### Steps:

1. Start a mysql server

```
mysql.server start --port=3307
```

2. Start a PostgreSQL that serves kviklet on port 5432

```
brew services start postgresql@16
```

3. Copy `application.yaml` and rename it to `application-local.yaml`. Then configure `application-local.yaml` with your datasource and initial user settings

4. Create a PostgreSQL database and set the database owner to the initial user specified in `application-local.yaml`.

5. Run the frontend:

```
   cd frontend/
   npm install
   npm run start
```

6. run the backend:

```
   cd backend/
```

Use the configuration in `application-local.yaml` and run:

```
   ./gradlew bootRun
```

To use `application-local.yaml`, run:

```
./gradlew bootRun -Dspring.profiles.active=local
```
