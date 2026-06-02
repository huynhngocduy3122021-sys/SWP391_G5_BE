# Copilot instructions for Parking Project

Purpose
- Spring Boot REST API for a parking system (user auth + parking slots). Code uses Java 17, Spring Boot, Spring Data JPA, Spring Security and JWT.

Build / Run / Test
- Build full project: mvn clean package
- Run locally (dev): mvn spring-boot:run
- Run jar: java -jar target/*.jar
- Server default port: 8081 (see src/main/resources/application.properties)

Testing
- Run full test suite: mvn test
- Run a single test class: mvn -Dtest=MyTestClass test
- Run a single test method: mvn -Dtest=MyTestClass#testMethod test

Linting / Static analysis
- No Checkstyle/PMD/SpotBugs plugins found in pom.xml. If required, prefer adding Maven plugins (checkstyle:checkstyle, pmd:pmd).

High-level architecture
- Entry point: Parking.Main (Spring Boot application)
- Packages:
  - Parking.controller — REST endpoints (/api/auth/**, /api/parking/**)
  - Parking.service — business logic (UserService, SlotService, TokenService)
  - Parking.repository — Spring Data JPA repositories
  - Parking.model — JPA entities (User, Slot)
  - Parking.dto — request/response DTOs under dto.request and dto.response
  - Parking.config — security (SecurityConfig), ModelMapper/OpenAPI configs, and JWT filter
  - Parking.exception — application-specific exception types and APIExceptionHandler
- Security: stateless JWT. SecurityConfig permits /api/auth/** and Swagger endpoints. TokenService generates/verifies JWTs; a custom filter extracts and authenticates tokens.
- Persistence: Microsoft SQL Server via JDBC; connection configured in src/main/resources/application.properties. JPA ddl-auto=update is enabled.
- API docs: springdoc-openapi provides Swagger UI at /swagger-ui/index.html (accessible without auth per SecurityConfig).

Key conventions & notes for Copilot
- DTO mapping: ModelMapper is used; prefer producing DTOs in dto.request/response and mapping through ModelMapper bean.
- Passwords: BCrypt via PasswordEncoder bean in SecurityConfig.
- JWT: TokenService currently contains a hard-coded SECRET_KET constant. When suggesting changes or running locally, recommend reading secret from an environment variable or Spring property (e.g., spring.jwt.secret or env JWT_SECRET).
- Controllers use RESTful patterns and return ResponseEntity; follow existing error handling via APIExceptionHandler.
- Database: application.properties contains clear SQL Server credentials and settings. For local work, prefer overriding via environment variables (SPRING_DATASOURCE_URL/USERNAME/PASSWORD) or a separate application-local.properties.
- Port override: server.port in application.properties (default 8081). When running tests or multiple services, set -Dserver.port or use environment variable.

Where to look first
- pom.xml — build, dependencies
- src/main/java/Parking/config — security, model mapper, swagger setup
- src/main/java/Parking/service — business logic and TokenService (JWT)
- src/main/resources/application.properties — DB and server settings

AI assistant behavior tips
- When proposing code changes involving secrets, recommend moving secrets to environment variables and do not commit plaintext secrets.
- Prefer changes that preserve existing controller routes and DTO shapes.
- For database migrations/DDL, point out that ddl-auto=update may modify schema; suggest using migrations (Flyway/Liquibase) for production readiness.

Changes made
- Created .github/copilot-instructions.md summarizing build/test commands, architecture, and key conventions.

If any area needs deeper coverage (tests, CI workflows, or environment-per-file examples), say which part to expand.