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
FROM tomcat:9.0.65-jdk11-openjdk-slim

# Set the working directory inside the container
WORKDIR /usr/local/tomcat

# Remove the default webapps to keep the image clean
RUN rm -rf webapps/*

# Copy the WAR file from the build stage
COPY --from=build /app/target/planmate-0.0.1-SNAPSHOT.war webapps/ROOT.war

# Ensure proper permissions
RUN chmod +x /usr/local/tomcat/bin/catalina.sh

# Ensure environment variables for Spring Boot
ENV JAVA_OPTS="-Dspring.config.location=file:/usr/local/tomcat/conf/application.properties"

# Copy Spring Boot config file if using it
COPY src/main/resources/application.properties /usr/local/tomcat/conf/

# Expose port 8080
EXPOSE 8080

# Start Tomcat
CMD ["catalina.sh", "run"]
