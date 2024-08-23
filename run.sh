#!/bin/bash
# Start Nginx and Spring Boot Service
nginx -g 'daemon off;' &

java -jar /app/app.jar

# Wait for any process to exit
wait -n

# Exit with status of process that exited first
exit $?