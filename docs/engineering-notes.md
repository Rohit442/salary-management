# Engineering Decisions and AI Usage

This log records adopted engineering decisions, their rationale, and supporting evidence.

## Decisions

| Decision | Reason |
|---|---|
| Manage annual base salaries and monthly base-pay estimates | Cover compensation maintenance and joining/leaving calculations |
| Keep monetary reports separated by currency | Avoid unsupported exchange-rate assumptions and misleading totals |
| Use Java and Spring Boot | Match the user's chosen ecosystem |
| Bootstrap with Java 21 and Spring Boot 3.5.16 | Pin the backend baseline in the Maven POM; use Boot dependency management for compatible Spring, H2, Liquibase, and test versions |
| Reuse the supplied Spring project bootstrap and Maven wrapper | Retain the existing POM and `com.acme.salarymanagement` package; use persistent H2 locally and isolated H2 databases for tests |
| Use AngularJS | Follow the assignment's Java-specific frontend wording |
| Use persistent H2 and Liquibase | Simplify clone/run, retain data across backend restarts, and apply schema/seed changes once |
| Build a modular monolith | Keep transactions, deployment, and internal responsibilities manageable |
| Use pre-provisioned HR accounts and server sessions | Support named HR identities and server-managed sessions |
| Save salary and audit history atomically with version checks | Preserve history consistency and prevent silent overwrites |
| Retain eight main screens | Cover employee maintenance, salary history, and compensation reporting |
| Use TDD for core behavior and bug fixes | Drive design with observable examples and fast feedback; refactor safely |
| Use focused units and constructor injection | Keep business behavior independently testable and dependencies explicit |
| Verify database behavior against H2 | Validate actual constraints, transactions, queries, and conflicting writes |
| Automate builds and tests in CI | Make verification reproducible and expose regressions early |
| Measure before scaling | Use query plans, workload measurements, and operational signals to justify optimization |
| Define four authentication endpoints with session cookies and explicit CSRF retrieval | Reuse Spring Security authentication/logout handling with predictable JSON responses for AngularJS |
| Use PUT for complete employee details and explicit inactive status | Match complete form/state submissions while preserving version checks and safe retry reconciliation |
| Separate salary operation keys from tracing IDs and retain committed replay outcomes | Prevent duplicate salary/history effects after uncertain retries and preserve original actor/name and resulting version |
| Return currency summaries and groups from one snapshot | Keep counts and monetary breakdown consistent without conversion |
| Record salary effective dates and preserve the initial salary baseline | Select the correct salary for a month while retaining recording-time audit history |
| Apply the latest raise within a month to the whole month | Follow the agreed monthly pay policy; prorate only joining and leaving days |
| Keep employment end date separate from inactive status | Preserve historical estimates and allow future end dates without disabling salary maintenance early |
| Supply backend-owned reference options | Keep forms, filters and currency precision aligned with validators |

AngularJS is a legacy choice: [official support ended in January 2022](https://github.com/angular/angular.js/security/policy). The choice follows the assessment wording. Record any approved framework change here.

H2 supersedes Oracle and PostgreSQL; Liquibase supersedes Flyway. The database runs inside the backend process and persists to a file.

## Engineering practice and evidence

Use the [testing strategy](testing-strategy.md) to apply red–green–refactor, select meaningful unit/integration/browser boundaries, and verify transactional and retry behavior. CI, observability, and deferred scaling concepts are defined in the [technical design](technical-design.md). Do not add infrastructure solely to demonstrate a pattern.

For an implemented behavior, record its test identifier, expected failing result, passing result after implementation, and relevant refactoring. Use actual command/CI output or commit references where available. Coverage percentages alone do not demonstrate correctness or TDD.

Reference guidance: [TDD](https://www.martinfowler.com/bliki/TestDrivenDevelopment.html), [test boundaries](https://www.martinfowler.com/articles/practical-test-pyramid.html), [continuous testing](https://dora.dev/capabilities/test-automation/), [safe retries](https://aws.amazon.com/builders-library/making-retries-safe-with-idempotent-APIs/), and [operational signals](https://sre.google/sre-book/monitoring-distributed-systems/).

## AI-assisted work and verification

| Work | AI contribution | Review and evidence |
|---|---|---|
| Requirements | Drafted and refined scope, exclusions, and correctness rules | User supplied the baseline, approved refinements, and selected H2; saved in [requirements](requirements.md) and [rules](requirements-rules.md) |
| UI planning and design | Converted supplied flows into FigJam diagrams; created eight editable Figma screens and supporting states | Main screens and supporting states reviewed; effective dates and monthly estimate designs updated, with manual layout/recovery repairs recorded in the [Figma handoff](design/figma-ui.md) |
| Design standards | Reviewed the supplied standards and produced an enhanced PDF | Generated PDF pages visually inspected; [saved standards](../output/pdf/salary-management-design-standards-v2.pdf) |
| API contracts | Added employment dates, salary effective dates/baseline, and the monthly base-pay endpoint | Validated JSON examples, shell/heredoc syntax, local links, endpoint/curl coverage, and independently checked example arithmetic; [API index](api-conventions.md) |
| Technical design | Proposed architecture, data model, transaction flow, and verification approach | Checked local links and the presence of three Mermaid blocks; [technical design](technical-design.md) |

Representative requests were “apply refinements,” “making the UI screens on figma,” and “use H2, and use liquibase to seed tables.” Retain relevant implementation prompts and verification evidence as development proceeds.

Add observed implementation results and significant decision changes to this log as work occurs.

## Verified sample backend — 25 October 2026

- Added persistent H2, Liquibase table/seed changesets, a generic Spring JDBC SQL utility, and the implemented `GET /api/sample` route.
- Test-first observation: `returnsGreetingReadFromSeededTable` failed with H2 `Table SAMPLE_MESSAGE not found` before adding the table, seed, and endpoint implementation.
- Maven `verify` passed: three tests, zero failures/errors. Tests verify the seeded API response, unauthenticated rejection, and file persistence after closing/reopening the application with no repeated seed or overwritten edits.
- Packaged application HTTP check returned 200 with `{"id":1,"message":"hello Rohit"}` using development authentication; the unauthenticated JSON request returned 401.
- Verification used IntelliJ's bundled JDK 25 with Java 21 compilation target. Tests use isolated databases; the live sample used the default persistent home-directory H2 file.

## First-run Datafaker seed — 25 October 2026

- Use the supplied Datafaker 2.7.0 dependency in a Liquibase `CustomSqlChange`. Generate typed insert statements in Java; Liquibase owns SQL execution, connection lifecycle, the seed transaction, and applied-change tracking. Existing sample changesets remain unchanged.
- The employee seed now runs automatically once, replacing the earlier plan for an explicitly selected demo context. Default: 10,000 employees. Fast integration fixture: 20. Fixed randomness and a fixed reference date make fictional names, amounts, dates, and identifiers reproducible.
- Added the employee table with unique employee IDs/emails, decimal salary/baseline fields, employment dates, version, timestamps, and basic integrity constraints. Salary-history and HR-authentication schema implementation remains tracked in the README.
- Test-first evidence: the file-restart integration test failed with H2 `Table EMPLOYEE not found` before the employee schema/seed was implemented.
- Maven `verify` passed: four tests, zero failures/errors. The full 10,000-row file database survived application restart with an edited employee name intact and exactly one recorded employee seed execution. The small fixture verified unique identities, five country/currency groups, and positive consistent salary baselines. Existing sample/authentication checks remain green.
- Configure Mockito as a startup Java agent for tests, avoiding dynamic self-attachment on the bundled JDK. Verification used JDK 25 with Java 21 compilation target.

## Authenticated H2 console — 25 October 2026

- Enable the local browser console at `/h2-console`, with remote console access disabled and an `H2_CONSOLE_ENABLED=false` switch for other environments.
- Scope same-origin framing and the CSRF exception to the console security chain. Retain authenticated API access, default API frame denial, and API CSRF protection in the fallback chain. Apply this configuration only in servlet applications with the console enabled.
- Observed the console HTTP test fail on `X-Frame-Options: DENY` before the security change.
- Maven `verify` passed: eight tests, zero failures/errors. Real HTTP tests verified the sign-in redirect, authenticated console frames, H2 database login and an employee-count SQL query. API authentication, CSRF rejection, and frame denial remain covered; sample and 10,000-employee persistence tests also pass.

## ER schema and employee seed upgrade — 25 October 2026

- Implement `HR_USER`, `CURRENCY`, `EMPLOYEE`, and `SALARY_CHANGE` from the saved ER model. Add the missing HR-user-to-created-employee relationship to the diagram. Use new Liquibase SQL DDL/change files instead of rewriting already-applied migrations or the original employee generator.
- Upgrade the existing employee table in place: widen API fields, add creation-actor snapshots/foreign keys and currency relationships, and backfill provenance from a disabled seed identity. Generate an unknown password and store only its BCrypt hash; provisioning actual HR login accounts remains separate.
- Seed five supported currencies and preserve all existing Datafaker employees. Start salary history empty because initial amounts belong in the employee baseline. History schema includes actor/retry uniqueness, version/effective-date uniqueness, currency consistency, and the employee/time/ID index.
- Retire the hello-world `SAMPLE_MESSAGE` table and its `/api/sample` implementation through a new drop-table migration and removal of the sample controller/service files.
- Observed red tests before implementation: expected ER tables were missing, and the legacy-data upgrade test failed with `Table HR_USER not found`.
- Maven `verify` passed: ten tests, zero failures/errors. Tests cover fresh migrations, upgrade of an old 10,000-employee file database, a subsequent restart, preserved employee edits, complete creator/currency links, empty initial history, invalid foreign keys, duplicate replay keys, history currency mismatch, salary checks and API field lengths. H2 console SQL access and API security checks remain green. Database-backed business endpoints are tracked in the README.

## YAML-only changelog files — 25 October 2026

- Convert all four formatted-SQL changelog files to YAML, update master includes, and use native Liquibase changes for tables, columns, keys, indexes, backfill and removal. Keep check constraints in inline YAML `sql` blocks because the native check-constraint change requires the paid Liquibase edition. The existing creator lookup remains a computed SQL expression.
- Preserve the original changeset IDs/authors/logical paths. Capture the eight SQL checksums and the Java reference-seed checksum from a database created by the pre-conversion packaged build, and accept these nine specific legacy checksums via `validCheckSum`. Do not clear checksums, use a wildcard acceptance rule, or reset data/history.
- Test-first compatibility check: after native YAML conversion, restarting with recorded SQL-era checksums failed with eight checksum mismatches. Adding the exact accepted checksums made the regression pass, preserving the employee edit and the complete recorded migration metadata.
- Maven `verify` passed: eleven tests, zero failures/errors. Fresh schemas, the 10,000-employee legacy upgrade/restart, constraints, console SQL access, and API security checks remain green.

- Move all six static reference seed rows (one disabled HR provenance identity and five currencies) into native YAML `insert` changes in `006-seed-er-reference-data.yaml`; delete `ReferenceDataSeedChange`. Store the disabled identity's existing unknown-password BCrypt hash as seed data; no runtime Java password/hash generation is needed. The original employee Datafaker generator continues supplying statements to Liquibase, which performs the writes.
- Final `clean verify` passed with eleven tests. Clean packaging removes the retired SQL resources and Java reference-seeder class rather than carrying stale build output forward. The compatibility regression covers all nine legacy checksums and confirms migration history and edited data remain intact.
- Packaged application also reopened a real database created by the previous SQL/custom-seed build: Liquibase reported zero changes run and thirteen previously run, with the edited employee and original history retained. Package inspection confirmed there are no `.sql` changelog resources or `ReferenceDataSeedChange.class` in the application archive.

## Database-backed HR browser sign-in — 25 October 2026

- Implement the first login increment through Spring Security's existing browser form. `HrUserDetailsService` loads normalized work email, password hash, role, and enabled status from `HR_USER` through `SqlUtility`. Spring performs BCrypt verification and session authentication. No custom login page/controller is added.
- Add the user-approved local demo account `maya.rao@acme.example` with documented password `AcmeDev!2026`, stored only as a BCrypt hash in Liquibase YAML migration `010`. Its explicit `@demo` context is selected by default; a fresh database initialized with another context does not receive it. Context selection does not revoke a demo account already seeded into an existing database.
- Preserve the disabled seed provenance identity. Keep application security independent of H2 console enablement and require the HR role. Use one generic browser failure message for unknown users, wrong passwords, and disabled users; unexpected authentication-service failures are logged once without credentials.
- Observed failing tests first: database HR sign-in was unauthenticated and failure text differed from the generic message. After implementation, `clean verify` passed sixteen tests with zero failures/errors. Verification covers normalized email, generic failures, an actual browser-form HTTP session and session-ID rotation, authenticated console SQL access, console-disabled authentication, fresh-database demo exclusion, schema constraints, existing-database compatibility, and employee persistence.

## Authentication API tests: red phase — 25 October 2026

- Add contract-based tests for all four authentication APIs before implementing their routes. Keep request validation, credentials, session/CSRF lifecycle and error behavior tied to the saved contract. Do not weaken assertions, disable tests or alter the contract to accommodate the existing browser-only login.
- Add fourteen isolated unit regression cases for the existing user lookup and browser failure handler, separately from the Spring MVC/security API suite. Pure unit tests do not start Spring or H2; security-chain/session behavior uses integration tests.
- Observed red phase: all twenty-nine API cases fail on absent routes/JSON behavior, with zero setup/test errors. Existing browser/schema/persistence coverage remains green. Full `mvn test` result: fifty-nine cases, thirty passing, twenty-nine failures, zero errors/skips.
- No authentication API implementation was added in this step. The next TDD increment starts with anonymous CSRF issuance, makes its cases green, and proceeds through login, current-user and logout behavior with regression checks after each change.

### Interpretation of the authentication test results

The 30 passing cases cover existing code: nine user-details unit cases, five browser-failure-handler unit cases, and sixteen existing browser/database regression cases. All 29 new JSON authentication API cases fail. The full Maven run is a failed build, not an API implementation success. Commands, test boundaries, and the observed 59-case result are recorded in [the testing strategy](testing-strategy.md#authentication-test-results--25-october-2026). No new API passing result is claimed.

## CSRF API implementation — 25 October 2026

- Implement the approved CSRF increment with typed token/error DTOs, a dedicated bodyless-request validator, the requested controller error-list/try/catch pattern, and an authentication service interface whose default method throws `UnimplementedMethodException`.
- Scope the new security chain and request metadata filter to `/api/auth/**`; use session-backed CSRF, header-only API token resolution, disabled saved-request caching, no-store, server-generated request IDs and JSON security errors. Instantiate the filter only in that chain to avoid servlet-wide double registration. Keep browser/H2 security separate.
- Share the JSON error writer between security filters and MVC advice. Unexpected failures are logged through SLF4J at the handling boundary with the response request ID; validation/security rejections do not log submitted values. Re-throw unrelated MVC negotiation/method exceptions to preserve their existing handling.
- TDD evidence: anonymous CSRF issuance initially returned 401 instead of 200; all eight new request/token tests also failed before implementation. The focused nine-case run then passed. Add a saved-request regression check and strengthen actual header-token use and the authenticated-session fixture.
- Final full run: 68 tests, 43 passing, 25 failing, zero errors/skips. All 30 existing regressions remain green. Nine CSRF tests and four original contract cases pass. The remaining login/current-user/logout behavior stays red; the full build still fails. No database migration or seed modification was needed for CSRF.

## Responsibility packages — 25 October 2026

Move the flat authentication classes into `rest`, `service`, `impl`, `bean`, `util`, `validator`, `filter`, `handler`, `exception`, and `config` packages; mirror the related test packages and update documentation links. Move shared SQL/exception classes into `util`/`exception`. Class bodies remain unchanged; only package declarations/imports and physical paths change. A clean test run retains the same 68 cases, 43 passes and the identical 25 pending-API failures, with zero errors/skips.

## JSON login, current-user and logout — 25 October 2026

- Implement Spring-owned form authentication at `/api/auth/login` and POST-only logout, with JSON handlers and session-backed CSRF rotation. Validate request shape after CSRF but before Spring authentication/logout mutates sessions. Bound actual encoded bytes to 4,096, strictly decode UTF-8 form fields, and retain dedicated validation plus the requested controller/service pattern.
- Implement `/me` with a fresh account read and session invalidation on missing/disabled/non-HR access. Add native Liquibase migration `011` for opaque public user IDs and 255-character password hashes; retain the HR-only database constraint and existing accounts/seed history.
- Use synchronized rolling attempt windows by normalized email/servlet client IP, configurable limits and injected clock. Validation/CSRF/dependency failures do not count as bad credentials. Success clears the pair's failures.
- Retain legacy BCrypt hashes and choose PBKDF2 for newly encoded passwords to honor 128 characters without truncation. Tests first observed BCrypt encode failures on long ASCII/multibyte passwords; both formats now verify through the configured encoder.
- Reuse Spring password checks, pre-session HR permission verification, fixation protection and logout cleanup. Shared JSON errors carry the request ID; API MDC supplies it to framework internal-authentication error logs. Dependency errors return safe 503 responses.
- Observed first red checks: login/current-user/logout returned 401/404 rather than 200/204, and new limiter/password cases failed. The original 29 API cases then all passed. The final `clean verify` passed 81 tests with zero failures/errors/skips and packaged the backend, including eight real HTTP tests. Preserve the documented limits of the verification rather than claiming a deployed HTTPS or timed-expiry test.
