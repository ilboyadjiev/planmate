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
The easiest way to run the PostgreSQL databases locally is via Docker. 

Both the Postgres and the MongoDB are required.

Run the following command in the root directory. Replace "your_user" and "your_pass" with the username and password you would like to use for the local connection.


	docker run -d  --name planmate-postgres  -p 5432:5432 -e POSTGRES_USER=your_user -e POSTGRES_PASSWORD=your_pass -e POSTGRES_DB=your_db_name -v planmate-pg-data:/var/lib/postgresql/data postgres:14-alpine

Ensure your application.yml or application.properties is configured to point to localhost:5432 for local development.

To start a MongoDB container locally:


	docker run -d --name planmate-mongodb -p 27017:27017 -e MONGO_INITDB_ROOT_USERNAME=your_user -e MONGO_INITDB_ROOT_PASSWORD=your_pass -e MONGO_INITDB_DATABASE=planmate_nosql -v planmate-mongo-data:/data/db mongo:6

Ensure your application.yml or application.properties is configured to point to localhost:27017 for local development.



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