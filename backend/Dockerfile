# Stage 1: Build the application
FROM gradle:jdk-focal AS build

# Set the working directory
WORKDIR /home/gradle/src

# Copy the source code to the container
COPY --chown=gradle:gradle . .

# Build the application
RUN gradle build  -x kaptTestKotlin -x compileTestKotlin --no-daemon

# Stage 2: Run the application
FROM amazoncorretto:20-alpine-jdk

# Set the working directory
WORKDIR /app

# Copy the built jar file from the build stage
COPY --from=build /home/gradle/src/build/libs/*.jar app.jar

# Expose the application on port 8080
EXPOSE 8080
#EXPOSE 5438-6000

# Start the application
CMD ["java", "-jar", "app.jar"]
