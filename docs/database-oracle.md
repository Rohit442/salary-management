# Oracle Database Setup and Decision

## Selected database

ACME Salary Management uses **Oracle Database**. Oracle SQL is the dialect used for schema migrations and database-specific queries. The database choice supports transactional salary/history updates, relational constraints, decimal salary storage, and compensation reporting.

## Local tools

- JDK 21 and a Java IDE for the Spring Boot backend.
- Docker Desktop for Apple Silicon to run an ARM64-compatible Oracle Database Free container. Obtain the image through [Oracle's official setup instructions](https://www.oracle.com/database/free/get-started/).
- Oracle SQL Developer or DBeaver as an optional database client.
- The project's Maven Wrapper will manage Maven when the backend is generated. JDBC and migration dependencies belong in the backend build, not in manually downloaded application JARs.

Pin the Oracle image version in the eventual container configuration so local development and integration tests use a reproducible database version. Do not rely on an unpinned `latest` tag for the final setup.

## Spring configuration plan

Use Spring Web, Spring Data JPA, Spring Security, Validation, Oracle JDBC, and Flyway with Oracle support. Choose the exact driver and migration-module versions compatible with the selected Spring Boot and Oracle releases. Oracle documents its [Spring integration](https://www.oracle.com/apac/database/technologies/oracledb-and-spring-springboot.html), and Redgate documents [Flyway's Oracle support](https://documentation.red-gate.com/flyway/reference/database-driver-reference/oracle-database).

Keep the database connection URL, application schema username, and password in environment-specific configuration. The application should connect as its own schema user. Run versioned migrations and configure Hibernate to validate the schema rather than modifying it automatically.

## Data and verification

- Store salary amounts in fixed-precision `NUMBER` columns and map them to Java `BigDecimal`.
- Enforce employee ID/email uniqueness, foreign keys, required values, and positive amounts in the schema where appropriate.
- Preserve atomic salary/history writes and optimistic version checks.
- Seed 10,000 fictional employees using a repeatable Oracle-compatible process, including joining dates and immutable initial salary baselines. Include known effective-dated raises and leaving dates for monthly estimate demonstrations.
- Verify currency separation, aggregate calculations, median, display rounding, and date/time handling against known expected results.
- Run persistence and transaction tests against Oracle, with isolated test data. Keep validation/calculation unit tests fast and deterministic.

## Deployment

The deployed backend must connect to an Oracle Database instance. Select the deployment host and Oracle offering before provisioning. Confirm that the chosen environment fits the database edition's resource limits.
