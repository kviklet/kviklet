# Stage 1: Build the application
FROM gradle:jdk-focal AS build

# Set the working directory
WORKDIR /home/gradle/src

# Copy the source code to the container
COPY --chown=gradle:gradle ./backend .

# Build the applications
RUN gradle build  -x kaptTestKotlin -x compileTestKotlin -x test --no-daemon

FROM node:22 as build-frontend
WORKDIR /app
COPY ./frontend/package-lock.json ./frontend/package.json ./
RUN npm ci --production
COPY ./frontend .
RUN npm run build

# Stage 2: Run the application
FROM amazoncorretto:21

WORKDIR /app

# Install nginx and MySQL client
RUN amazon-linux-extras install -y nginx1 && \
    amazon-linux-extras enable mariadb10.5 && \
    yum clean metadata && \
    yum install -y mariadb

COPY ./frontend/docker/nginx/conf.d/default.conf /etc/nginx/conf.d/default.conf
COPY --from=build-frontend /app/build /usr/share/nginx/html

# Copy the built jar file from the build stage
COPY --from=build /home/gradle/src/build/libs/*.jar app.jar

COPY --chmod=755 ./run.sh .

EXPOSE 80

# Start the application
CMD ["/usr/bin/sh", "./run.sh"]
