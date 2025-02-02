FROM gradle:jdk-jammy AS build
WORKDIR /home/gradle/src

COPY --chown=gradle:gradle ./backend .

RUN gradle build  -x kaptTestKotlin -x compileTestKotlin -x test --no-daemon

FROM node:22 as build-frontend
WORKDIR /app
COPY ./frontend/package-lock.json ./frontend/package.json ./
RUN npm ci --production
COPY ./frontend .
RUN npm run build

# Needed to copy a working Java runtime to the final image
FROM amazoncorretto:21 AS javaruntime

FROM nginxinc/nginx-unprivileged:1.27

WORKDIR /app

USER root

RUN ln -sf /dev/stdout /var/log/nginx/access.log \
    && ln -sf /dev/stderr /var/log/nginx/error.log

RUN apt-get update && \
    DEBIAN_FRONTEND=noninteractive apt-get install -y mariadb-client && \
    apt-get clean && \
    rm -rf /var/lib/apt/lists/*


COPY --from=javaruntime /usr/lib/jvm/java-21-amazon-corretto /usr/lib/jvm/java-21-amazon-corretto

# Set Java environment variables
ENV JAVA_HOME=/usr/lib/jvm/java-21-amazon-corretto
ENV PATH="${JAVA_HOME}/bin:${PATH}"

USER nginx

COPY --chown=nginx:nginx ./frontend/docker/nginx/conf.d/default.conf /etc/nginx/conf.d/default.conf
COPY --from=build-frontend --chown=nginx:nginx /app/build /usr/share/nginx/html
COPY --from=build --chown=nginx:nginx /home/gradle/src/build/libs/*.jar app.jar
COPY --chown=nginx:nginx --chmod=755 ./run.sh .

EXPOSE 8080

CMD ["/usr/bin/bash", "./run.sh"]
