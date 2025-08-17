# Stage 1: Build the application
FROM gradle:jdk21-noble AS build

# Set the working directory
WORKDIR /home/gradle/src

# Copy the source code to the container
COPY --chown=gradle:gradle . .

# Build the application
RUN gradle assemble --no-daemon

# Stage 2: Run the application
FROM amazoncorretto:21-alpine-jdk

# Set the working directory
WORKDIR /app

# Copy the built jar file from the build stage
COPY --from=build /home/gradle/src/build/libs/*.jar app.jar

# Expose the application on port 8080
EXPOSE 8080
#EXPOSE 5438-6000

# Start the application
CMD ["java", "-jar", "app.jar"]
