FROM gradle:8.14.3-jdk21 AS build
WORKDIR /home/gradle/src

COPY --chown=gradle:gradle ./backend .

RUN gradle --version --no-daemon

RUN gradle build  -x kaptTestKotlin -x compileTestKotlin -x test --no-daemon

# Build a trimmed Java runtime with jlink. Module list: `jdeps --print-module-deps`
# on the boot jar, plus modules loaded reflectively/via service-loader that jdeps
# cannot see:
#   jdk.crypto.ec                     - EC TLS ciphers (outbound HTTPS, SSO)
#   jdk.security.auth/jdk.security.jgss - JAAS/Kerberos (MSSQL integrated auth, SPNEGO)
#   jdk.naming.dns                    - JNDI DNS provider (LDAP server discovery)
#   jdk.charsets, jdk.localedata      - extended charsets/locales for DB connections
#   jdk.zipfs, jdk.management         - nested-jar access, JVM management extensions
RUN jlink \
    --add-modules java.base,java.compiler,java.desktop,java.instrument,java.management,java.naming,java.net.http,java.prefs,java.rmi,java.scripting,java.security.jgss,java.security.sasl,java.sql,java.sql.rowset,java.transaction.xa,java.xml,java.xml.crypto,jdk.charsets,jdk.crypto.ec,jdk.httpserver,jdk.jfr,jdk.localedata,jdk.management,jdk.naming.dns,jdk.net,jdk.security.auth,jdk.security.jgss,jdk.unsupported,jdk.zipfs \
    --strip-debug --no-man-pages --no-header-files --compress=zip-6 \
    --output /opt/java-runtime \
    # cacerts must be a real, populated keystore — a symlink would dangle in the
    # final image and leave the JVM without trust anchors
    && test ! -L /opt/java-runtime/lib/security/cacerts \
    && /opt/java-runtime/bin/keytool -list -keystore /opt/java-runtime/lib/security/cacerts -storepass changeit | grep -q "trustedCertEntry"

FROM node:22 AS build-frontend
WORKDIR /app
COPY ./frontend/package-lock.json ./frontend/package.json ./
RUN npm ci --production
COPY ./frontend .
RUN npm run build

FROM nginxinc/nginx-unprivileged:1.27

# Build arguments for version info
ARG VERSION=dev
ARG BUILD_DATE=unknown
ARG GIT_COMMIT=unknown

WORKDIR /app

USER root

RUN ln -sf /dev/stdout /var/log/nginx/access.log \
    && ln -sf /dev/stderr /var/log/nginx/error.log

RUN apt-get update && \
    DEBIAN_FRONTEND=noninteractive apt-get install -y mariadb-client && \
    apt-get clean && \
    rm -rf /var/lib/apt/lists/*


COPY --from=build /opt/java-runtime /opt/java-runtime

# Set Java environment variables
ENV JAVA_HOME=/opt/java-runtime
ENV PATH="${JAVA_HOME}/bin:${PATH}"

# Set version info environment variables (Spring Boot relaxed binding maps these)
ENV APP_VERSION=${VERSION}
ENV APP_BUILD_DATE=${BUILD_DATE}
ENV APP_GIT_COMMIT=${GIT_COMMIT}

USER nginx

COPY --chown=nginx:nginx ./frontend/docker/nginx/conf.d/default.conf /etc/nginx/conf.d/default.conf
COPY --from=build-frontend --chown=nginx:nginx /app/build /usr/share/nginx/html
COPY --from=build --chown=nginx:nginx /home/gradle/src/build/libs/*.jar app.jar
COPY --chown=nginx:nginx --chmod=755 ./run.sh .

EXPOSE 8080

CMD ["/usr/bin/bash", "./run.sh"]
