#!/bin/bash
# Set default JAVA_OPTS if not already set
JAVA_OPTS=${JAVA_OPTS:-"-Xmx64m -Xms64m"}

# Start Nginx
nginx -g 'daemon off;' &
NGINX_PID=$!

# Start Spring Boot Service
java $JAVA_OPTS -jar /app/app.jar &
SPRING_PID=$!

# Wait for either process to exit
wait -n $NGINX_PID $SPRING_PID

# Exit with status of process that exited first
exit $?
