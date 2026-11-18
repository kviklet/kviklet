#!/bin/bash
# Start Nginx
nginx -g 'daemon off;' &
NGINX_PID=$!

# Start Spring Boot Service
# If JAVA_OPTS is set, use it. Otherwise, JVM uses its container-aware defaults
# -XX:-UseBASE64Intrinsics: the JIT's Base64 intrinsic can corrupt memory on malformed input
# (CVE-2026-114938), and Base64 from unauthenticated requests gets decoded (session cookie, tokens).
java -XX:-UseBASE64Intrinsics ${JAVA_OPTS} -jar /app/app.jar &
SPRING_PID=$!

# Wait for either process to exit
wait -n $NGINX_PID $SPRING_PID

# Exit with status of process that exited first
exit $?