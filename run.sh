#!/bin/bash
# Start Nginx
nginx -g 'daemon off;' &
NGINX_PID=$!

# Start Spring Boot Service
java -jar /app/app.jar &
SPRING_PID=$!

# Wait for either process to exit
wait -n $NGINX_PID $SPRING_PID

# Exit with status of process that exited first
exit $?