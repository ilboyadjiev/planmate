# Stage 1: Build the application
FROM maven:3.8.5-openjdk-11-slim AS build

# Set the working directory inside the container
WORKDIR /app

# Copy the pom.xml file and download dependencies
COPY pom.xml .
RUN mvn dependency:go-offline

# Copy the source code
COPY src ./src

# Build the application
RUN mvn clean package -DskipTests

# Stage 2: Run the application
FROM eclipse-temurin:11-jre-jammy

WORKDIR /app

# Copy the WAR file from the build stage, but rename it to app.war
COPY --from=build /app/target/planmate-0.0.1-SNAPSHOT.war app.war

# Commit SHA reported by /api/health, so the deploy smoke test can verify the new build is live
ARG GIT_SHA=unknown
ENV APP_VERSION=$GIT_SHA

# Expose port 8080
EXPOSE 8080

# Start the application
CMD ["java", "-Dspring.profiles.active=prod", "-jar", "app.war"]
