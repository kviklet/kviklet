#!/bin/bash
# Start Nginx
nginx -g 'daemon off;' &
NGINX_PID=$!

# Start Spring Boot Service
# If JAVA_OPTS is set, use it. Otherwise, JVM uses its container-aware defaults
java ${JAVA_OPTS} -jar /app/app.jar &
SPRING_PID=$!

# Wait for either process to exit
wait -n $NGINX_PID $SPRING_PID

# Exit with status of process that exited first
exit $?