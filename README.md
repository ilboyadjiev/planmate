# Planmate Backend

The core backend service for the Planmate ecosystem. This RESTful API is built with Spring Boot and provides secure, high-performance data management for users, social connections, and calendar events. It utilizes Spring Security with JWTs for stateless authentication and PostgreSQL for persistent data storage.

## Tech Stack

* **Language:** Java 11+
* **Framework:** Spring Boot 2.7.12
* **Data Access:** Spring Data JPA / Hibernate
* **Database:** PostgreSQL
* **Security:** Spring Security & JSON Web Tokens (JWT)
* **Build Tool:** Maven (or Gradle)
* **Containerization:** Docker & Docker Compose
* **Documentation:** OpenAPI / Swagger

---

## Getting Started / Local Development

### Prerequisites
* Java 11
* Docker & Docker Compose (for local database)
* IDE of your choice (IntelliJ IDEA, Eclipse, VS Code)

### 1. Database Setup
The easiest way to run the PostgreSQL database locally is via Docker. 
Run the following command in the root directory:


	docker-compose up -d db


Ensure your application.yml or application.properties is configured to point to localhost:5432 for local development.

### 2. Environment Variables ###

You will need to configure the following environment variables (or define them in a .env file / application properties) before running the app:

    DB_URL: jdbc:postgresql://localhost:5432/planmate

    DB_USERNAME: your_db_username

    DB_PASSWORD: your_db_password

    JWT_SECRET: A secret key for signing tokens
    
### 3. Run the Application ###

Run the Spring Boot application using your IDE, or via the command line:


	./mvnw spring-boot:run


The server will start on http://localhost:8080

### API Documentation ###

When running locally, full interactive API documentation is available via Swagger UI at http://localhost:8080/swagger-ui.html

### License ### 

This project is licensed under the MIT License.