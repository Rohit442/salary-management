# ACME Salary Management

## Project status

Requirements, API contracts, technical design, eight Figma screens, and the backend bootstrap are available. The backend includes persistent H2, the four ER tables, Liquibase migrations, 10,000 seeded employees, database-backed HR browser sign-in, the local H2 console, all four JSON authentication APIs, the compensation-report and reference-data APIs, and all employee creation, detail, status, salary/history and monthly base-pay APIs. All 15 contracted backend APIs are implemented, including the separate employee name-suggestions lookup. GitHub Actions build/test automation is configured; non-demo HR provisioning and deployment remain pending.

The latest local Maven `clean verify` passed **227 tests with zero failures/errors/skips** and packaged the backend. This includes forced database failures proving transaction rollback, controlled concurrent report/estimate snapshots, calendar-day pay boundaries, employee writes, salary replay/history, Unicode storage, and a complete HTTP business workflow. JaCoCo measured **90.58% line and 81.19% branch coverage** across the backend; verification enforces minimums of 80% and 70%. Test databases and random-port servers are isolated from your running application on port 8080.

The AngularJS frontend now implements all eight Figma pages with responsive layouts. Sign-in, employee directory/details, reference options and compensation reports are verified against the real APIs. Employee write, history and estimate screens use the contracted routes, which are now implemented. Salary amount and effective-date fields validate on blur, the workspace's ACME logo/name links to Employees, and Sign out stays visible in short mobile menus. Employee ID errors state the six-digit format; browser validation covers every input-bearing flow, including Unicode length boundaries. Live browser verification also covers salary updates and the immutable initial-salary history response. Frontend verification: **32 unit tests, 504 fixture checks across Chromium/Firefox/WebKit, and 26 isolated live-API browser workflows passed**; the production asset build passes. API, formatting and validation unit coverage is 96.91% lines and 88.67% branches. Restart the backend to apply migration `013`, which preserves existing records while accommodating the documented Unicode text limits.

The requested [directory revision](docs/design/figma-ui.md#directory-revision) is implemented in the frontend: names-only suggestions below the search box that respect the applied category filters, filters applied when focus leaves a control, fixed 100-row pages without a size selector, direct-page navigation and restored directory context after employee review. Existing `q` substring semantics, backend pagination defaults and API regression expectations remain unchanged; the separate authenticated suggestions endpoint is implemented and verified through the real frontend. The Figma preview is updated and visually checked, with a saved review image.

Feature exclusions are defined in [Out of scope](docs/requirements.md#out-of-scope).

## Planned application stack

Java with Spring Boot, Spring Data JPA, Spring Security, AngularJS, and **H2**, using versioned Liquibase YAML changelogs. Local runs retain data in a file; tests use isolated databases. See the [product requirements](docs/requirements.md) and [H2 database setup guidance](docs/database-h2.md).

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

To browse tables while the backend runs, open [http://localhost:8080/h2-console](http://localhost:8080/h2-console). Sign in with `maya.rao@acme.example` and the local demo password `AcmeDev!2026`, then connect using the default H2 JDBC URL, username `sa`, and an empty database password. [Console connection details](docs/database-h2.md#browser-console). Set `H2_CONSOLE_ENABLED=false` outside local development.

### Development HR sign-in

Restart the backend and open [http://localhost:8080/login](http://localhost:8080/login), or open the H2 console and follow its sign-in redirect.

| Field | Local demo value |
|---|---|
| Username / work email | `maya.rao@acme.example` |
| Password | `AcmeDev!2026` |

Spring Security authenticates against `HR_USER` using stored password hashes. Existing BCrypt hashes remain supported; newly encoded passwords use PBKDF2 to support the full 128-character contract without truncation. The `010` Liquibase YAML seed creates the enabled demo account once; the existing seed audit actor remains disabled. Email is matched after trimming and lowercasing. Wrong passwords, unknown emails, and disabled users show the same failure message. Successful browser sign-in uses a server session and rotates its ID.

The default Liquibase context is `demo`. For a fresh database without the demo account, set `LIQUIBASE_CONTEXTS=production` before initialization and provision real accounts separately. Changing the context on a database that already contains the demo account does not remove or disable it. Application authentication remains enabled when the H2 console is disabled.

From `backend/`, use the supplied Maven wrapper:

| Command | Purpose |
|---|---|
| `./mvnw clean verify` | Compile, run tests, and package the application |
| `./mvnw spring-boot:run` | Start the backend with the configured H2 connection |

On Windows, use `mvnw.cmd` in place of `./mvnw`.

## Authentication APIs and tests

Import [the Postman collection](docs/postman/acme-auth.postman_collection.json), keep cookies enabled, and run requests 01–03 to sign in. The collection documents the earlier 14 APIs across 15 requests, with logout last (15). Requests 09–14 demonstrate the employee creation, editing, salary/history, estimate and inactivation workflow. It includes the local demo variables and saves/refreshes the CSRF token automatically.

| Method | Path | Result |
|---|---|---|
| GET | `/api/auth/csrf` | Anonymous session-backed CSRF token |
| POST | `/api/auth/login` | Form-encoded email/password; JSON HR identity and session |
| GET | `/api/auth/me` | Current HR identity, with live enabled/role checks |
| POST | `/api/auth/logout` | Session invalidation, expired cookie, empty 204 response |

Restart the backend after the code changes. Follow the complete cookie-jar/curl flow in [the authentication contract](docs/api-auth.md#3-shared-curl-setup), using the local demo credentials above. Send the initial token and its cookie when logging in; fetch a fresh token after login for subsequent writes and logout.

From `backend/`:

```sh
./mvnw -Dtest=AuthenticationApiContractTest test
./mvnw -Dtest=AuthenticationHttpTests test
./mvnw clean verify
```

The contract suite checks all four routes, JSON errors, validation, CSRF/session rotation, revocation and attempt limiting. HTTP tests additionally exercise raw/chunked form bodies, malformed UTF-8/escaping, 4,096/4,097-byte limits, cookies, and stale-session rejection. Password and rolling-window limiter rules also have isolated unit tests with a controllable clock.

Configuration: `AUTH_IDLE_TIMEOUT_SECONDS` (1800), `AUTH_MAXIMUM_FAILURES` (5), `AUTH_ATTEMPT_WINDOW_SECONDS` (900), and `SESSION_COOKIE_SECURE` (false locally; true for HTTPS deployment). Client IP comes from the servlet container; arbitrary forwarded headers are not read by the limiter.

## Dashboard APIs

After signing in, use the same session cookie for these GET requests; no CSRF header is needed:

| Method | Path | Result |
|---|---|---|
| GET | `/api/reference-data` | Country, department, level and currency filter options |
| GET | `/api/reports/compensation` | Active employee count and annual base salary totals, averages and medians per currency |

Optional repeated report filters: `country`, `department`, `jobLevel`, `currency`. Repeat `groupBy` for `country`, `department` and/or `jobLevel`; currency is always included. For example: `/api/reports/compensation?country=IN&jobLevel=L4&groupBy=department`.

Restart the backend to load these routes. Full request/response and curl examples are in the [report contract](docs/api-reports.md) and [reference-data contract](docs/api-reference-data.md). Run dashboard checks with `./mvnw -Dtest=DashboardApiTests,DashboardFailureTests test` from `backend/`.

## Employee read APIs

- `GET /api/employees`: paginated directory with literal name/ID search, canonical filters and supported sorting; includes active and inactive employees.
- `GET /api/employees/suggestions?prefix=R&limit=8`: names beginning with the typed prefix; returns only public employee IDs and full names.
- `GET /api/employees/{employeeId}`: complete employee record, including the current version. Invalid IDs return 400; missing valid IDs return 404.

All three use the authenticated HR session without CSRF for GET. Restart the backend after code changes. The [employee contract](docs/api-employees.md#5-get-apiemployees) includes query rules and curl examples. The suggestion request and response are documented in the [employee contract](docs/api-employees.md#51-get-apiemployeessuggestions).

## Employee writes, salary history and monthly estimates

- `POST /api/employees`: create an employee and immutable initial salary baseline; returns 201 and Location.
- `PUT /api/employees/{employeeId}/details`: replace editable details with a reviewed version.
- `PUT /api/employees/{employeeId}/status`: set inactive with a reviewed version.
- `POST /api/employees/{employeeId}/salary-changes`: record a salary change using CSRF, version and a stable Idempotency-Key.
- `GET /api/employees/{employeeId}/salary-changes`: paginated immutable salary history.
- `GET /api/employees/{employeeId}/base-pay-estimate?month=YYYY-MM`: monthly base pay using whole-month salary changes and inclusive employment dates.

Restart the backend to load the new routes and Liquibase migration 012. Existing records are retained. Postman requests 09–14 exercise these operations with a separate managed employee. Refresh the CSRF token after signing in and review versions before edits. Reuse the captured salary payload/key for an uncertain retry.

## Project documentation

- [Requirements](docs/requirements.md) and [detailed business rules](docs/requirements-rules.md)
- [Technical design](docs/technical-design.md), including architecture, database ER, and salary-update sequence diagrams
- [Authentication API contract](docs/api-auth.md): CSRF, sign-in, current user and sign-out, with request/response and curl examples
- [Employee API contract](docs/api-employees.md): directory, details, creation and versioned PUT operations for details/status
- [Shared API conventions and endpoint index](docs/api-conventions.md): all 15 endpoints, common errors and retry rules
- [Salary/history API contract](docs/api-salaries.md): atomic changes, versions, idempotent retries and paginated history
- [Compensation report API contract](docs/api-reports.md): filters, grouping and currency-separated results
- [Monthly base-pay API contract](docs/api-base-pay.md): effective salary selection, whole-month raises, and joining/leaving proration
- [Reference-data API contract](docs/api-reference-data.md): backend-owned form/filter options and currency precision
- [Engineering decisions and AI usage](docs/engineering-notes.md)
- [Testing strategy and verification plan](docs/testing-strategy.md), including TDD, H2 checks, CI and measurement evidence
- [H2 database setup and decision](docs/database-h2.md)
- [Figma screens and design handoff](docs/design/figma-ui.md)

## Frontend setup and verification

Use Node.js 26 and npm. AngularJS 1.8.3, Angular Material 1.2.5, Vite and Roboto are pinned and built locally; runtime assets do not depend on a CDN.

```sh
npm ci
npx playwright install chromium
npm run dev
```

Open http://127.0.0.1:5173. Start the current backend build on port 8080 first; the development server forwards `/api` to it. To target another local backend, set `ACME_API_URL`, for example `ACME_API_URL=http://127.0.0.1:18081 npm run dev`. Use the demo HR credentials documented above. Authentication and CSRF state remain in memory; the browser owns the HttpOnly session cookie.

```sh
npm run test:unit
npm run test:coverage
npm run test:frontend
npm run build
```

The frontend Playwright configuration starts Vite automatically when needed. Its 168 checks per browser cover workflows, directory suggestions and filters, salary field validation and retry recovery, confirmation cleanup, keyboard/mobile navigation, desktop/mobile rendering and resource-URL security. Workflow data uses isolated contract fixtures. They cover all eight pages, errors, conflicts and uncertain retries, but do not prove backend persistence. Chromium rendering checks compare 16 committed macOS screenshot baselines; other engines retain structural rendering checks. Generated screenshots and reports are ignored by Git.

After building the latest backend JAR with the backend's `./mvnw clean verify` command, run:

```sh
npm run test:frontend:live
```

This requires Java 21+ on PATH and free ports 18080 and 5174. Playwright starts and stops separate backend/frontend processes and a temporary H2 database with 20 employees. The 26 checks verify actual sign-in, employee search/details, reports and filters, salary/history, Add/Edit validation and persisted boundary values, session restoration, and logout. It does not touch the persistent database or the backend on port 8080.

`npm run test:e2e` includes the starter and frontend fixture checks across Chromium, Firefox and WebKit; install those browsers first with `npx playwright install`. The frontend suite has also passed all 504 checks across Chromium, Firefox and WebKit. `npm run test:e2e:report` opens the latest HTML report.

`npm run build` writes `frontend/dist`. Production hosting still needs static assets served anonymously with same-origin `/api` forwarding; the build is not copied into the Spring JAR. AngularJS/Angular Material are the adopted legacy stack, with their support limitation recorded in [engineering notes](docs/engineering-notes.md). The dependency audit flags two affected packages: AngularJS (high, ten underlying advisories) and Angular Material (moderate, inherited through AngularJS). Vite also reports a JavaScript chunk over 500 kB. These dependency and bundle findings remain unresolved. The application now rejects untrusted Angular resource URLs with an empty trusted-resource list; this hardens its template/resource loading without patching AngularJS or clearing the advisory. See the [resource-URL mitigation evidence](docs/engineering-notes.md#angularjs-resource-url-policy--2026-10-26).

## GitHub Actions builds

[Build and test](.github/workflows/ci.yml) runs on every push, pull request and manual dispatch. It runs backend verification and coverage gates, frontend unit coverage, all three browser engines, visual regression checks and the isolated live backend suite, then builds the frontend. Successful runs retain the executable backend JAR and frontend assets; test/coverage reports are retained even on failure for 14 days. The workflow uses macOS 15 for the committed screenshot baselines and has no deployment step. It has been checked locally; no GitHub-hosted run is claimed until the changes are pushed.

Backend coverage report: `backend/target/site/jacoco/index.html` after `./mvnw verify`. Frontend coverage: `npm run test:coverage`. Thresholds and screenshot update instructions are in the [testing strategy](docs/testing-strategy.md#continuous-integration-and-reproducibility).
