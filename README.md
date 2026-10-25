# ACME Salary Management

## Project status

Requirements, API contracts, technical design, eight Figma screens, and the backend bootstrap are available. The backend includes persistent H2, the four ER tables (`HR_USER`, `CURRENCY`, `EMPLOYEE`, `SALARY_CHANGE`), Liquibase migrations, first-run Datafaker seeding of 10,000 employees, and the authenticated local H2 console. Salary-management APIs, database-backed HR sign-in/provisioning, CI, and deployment are pending. The current Playwright starter verifies browser setup.

Feature exclusions are defined in [Out of scope](docs/requirements.md#out-of-scope).

## Planned application stack

Java with Spring Boot, Spring Data JPA, Spring Security, AngularJS, and **H2**, using versioned Liquibase changelogs. Local runs retain data in a file; tests use isolated databases. See the [product requirements](docs/requirements.md) and [H2 database setup guidance](docs/database-h2.md).

## Backend setup in IntelliJ IDEA

1. Open [backend/pom.xml](backend/pom.xml) as a Maven project.
2. In **File → Project Structure → Project → SDK**, select **Download JDK** and choose Java 21, or select an existing JDK 21 installation.
3. Set the Maven importer and runner to use the project JDK, then reload the Maven project. IntelliJ's bundled Maven can resolve the dependencies.

The POM pins Spring Boot 3.5.16 and lets its dependency management select compatible library versions. It includes Spring Web, Security, Validation, Data JPA, H2 JDBC, Liquibase, Datafaker 2.7.0, and unit/security test dependencies. Spring Boot's default logging supplies SLF4J. The Data JPA starter also supplies Spring JDBC for the connection-managed SQL utility.

JDK instructions: [IntelliJ IDEA SDK setup](https://www.jetbrains.com/help/idea/sdk.html).

### H2 configuration and backend commands

No database installation is needed. The default H2 file lives at `~/.acme-salary-management/salary-management.mv.db` and survives backend restarts. Optional `DB_URL`, `DB_USERNAME`, and `DB_PASSWORD` override the defaults. Remove old Oracle overrides before running.

Liquibase creates the ER schema, seeds five currencies and a disabled employee-seed audit identity, and populates `EMPLOYEE` with 10,000 fictional employees. Employee creation actors and currencies are linked by foreign keys. `SALARY_CHANGE` starts empty: initial salaries are stored in employee baseline fields.

Existing databases are upgraded in place without rerunning the employee seed or overwriting employee edits. The hello-world table and `/api/sample` endpoint have been retired. Tests use separate databases with a 20-employee fixture; the upgrade/restart test preserves the full 10,000-record dataset. See [H2 schema and migration details](docs/database-h2.md).

To browse tables while the backend runs, open [http://localhost:8080/h2-console](http://localhost:8080/h2-console). Sign in with `user` and the generated startup password, then connect using the default H2 JDBC URL, username `sa`, and an empty database password. [Console connection details](docs/database-h2.md#browser-console). Set `H2_CONSOLE_ENABLED=false` outside local development.

From `backend/`, use the supplied Maven wrapper:

| Command | Purpose |
|---|---|
| `./mvnw clean verify` | Compile, run tests, and package the application |
| `./mvnw spring-boot:run` | Start the backend with the configured H2 connection |

On Windows, use `mvnw.cmd` in place of `./mvnw`.

## Project documentation

- [Requirements](docs/requirements.md) and [detailed business rules](docs/requirements-rules.md)
- [Technical design](docs/technical-design.md), including architecture, database ER, and salary-update sequence diagrams
- [Authentication API contract](docs/api-auth.md): CSRF, sign-in, current user and sign-out, with request/response and curl examples
- [Employee API contract](docs/api-employees.md): directory, details, creation and versioned PUT operations for details/status
- [Shared API conventions and endpoint index](docs/api-conventions.md): all 14 endpoints, common errors and retry rules
- [Salary/history API contract](docs/api-salaries.md): atomic changes, versions, idempotent retries and paginated history
- [Compensation report API contract](docs/api-reports.md): filters, grouping and currency-separated results
- [Monthly base-pay API contract](docs/api-base-pay.md): effective salary selection, whole-month raises, and joining/leaving proration
- [Reference-data API contract](docs/api-reference-data.md): backend-owned form/filter options and currency precision
- [Engineering decisions and AI usage](docs/engineering-notes.md)
- [Testing strategy and verification plan](docs/testing-strategy.md), including TDD, H2 checks, CI and measurement evidence
- [H2 database setup and decision](docs/database-h2.md)
- [Figma screens and design handoff](docs/design/figma-ui.md)

## Requirements

Node.js 26 and npm.

## Install

```sh
npm ci
npx playwright install
```

## Browser tests

```sh
npm run test:e2e
npm run test:e2e:ui
npm run test:e2e:headed
npm run test:e2e:report
```

Tests run in Chromium, Firefox, and WebKit. To run Chromium only:

```sh
npm run test:e2e -- --project=chromium
```

The starter test checks browser rendering and input without a network connection. During application implementation, configure Playwright's `webServer` and `baseURL` and add employee-search, salary-update, and authorization tests.

Official documentation: https://playwright.dev/docs/intro
