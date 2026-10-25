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

### Dashboard reporting implementation — 2026-10-25

- `GET /api/reports/compensation` and `GET /api/reference-data` use the existing session authentication, live enabled-HR checks and shared JSON errors. Report filters use bound SQL parameters.
- One employee/currency SELECT captures the matching population and currency precision in one H2 statement snapshot. At the required 10,000-employee scale, aggregate the returned rows in Java using BigDecimal, exact sorted medians and HALF_UP display rounding. Summaries use employee rows directly, independently of breakdown groups. This keeps the implementation readable and portable; larger datasets would justify database aggregation and profiling.
- Backend country/department/level vocabulary is shared with the repeatable employee seed without changing its ordering. Currency names and precision come from the Liquibase-managed CURRENCY table. No migration or reseeding is needed.
- TDD evidence: all initial 23 dashboard cases failed against absent routes, then passed after implementation. A subsequent method test exposed HEAD returning 200; explicit GET-only protocol validation fixes it. Tests use isolated H2 with independent salary fixtures; failure classification is additionally checked with SQL utility mocks.
- Final verification: Maven `clean verify` passed 107 tests, with zero failures/errors/skips, and packaged the executable backend JAR. Dashboard tests account for 24 H2/MockMvc cases and two failure-classification unit tests. The existing eight actual HTTP authentication cases also remain green. No manual dashboard curl run was performed.

### Employee directory and detail reads — 2026-10-25

- Implemented GET `/api/employees` and `/api/employees/{employeeId}` with responsibility-based packages, dedicated request validation, typed responses and SQL through SqlUtility. Existing HR session checks, request IDs, no-store headers and JSON error handling apply to both routes.
- Directory values are bound parameters; filter/sort columns come from fixed maps. Escape `!`, `%` and `_` before literal LIKE search. Use case-insensitive text sorting and internal ID ascending to break ties. Count and page queries share a Spring-managed read-only SERIALIZABLE H2 transaction, preserving a consistent view even for out-of-range pages.
- TDD evidence: 20 H2/MockMvc cases failed against missing routes, then passed after implementation. They cover pagination, literal search, filters, sorting, inactive records, complete detail shape, validation and authorization. No applied migration or local database was changed. Added both GET requests before logout in Postman.
- Final Maven `clean verify`: 127 tests, zero failures/errors/skips; executable JAR packaged. Updated the older H2 console test, which expected a missing employee route, to verify the implemented API rejects Basic credentials in favor of session authentication. Postman JSON and all 12 embedded script syntax checks passed; no Postman runner execution was performed.

### Employee directory and detail reads — 2026-10-25

- Implemented GET `/api/employees` and `/api/employees/{employeeId}` with responsibility-based packages, dedicated request validation, typed responses and SQL through SqlUtility. Existing HR session checks, request IDs, no-store headers and JSON error handling apply to both routes.
- Directory values are bound parameters; filter/sort columns come from fixed maps. Escape `!`, `%` and `_` before literal LIKE search. Use case-insensitive text sorting and internal ID ascending to break ties. Count and page queries share a Spring-managed read-only SERIALIZABLE H2 transaction, preserving a consistent view even for out-of-range pages.
- TDD evidence: 20 H2/MockMvc cases failed against missing routes, then passed after implementation. They cover pagination, literal search, filters, sorting, inactive records, complete detail shape, validation and authorization. No applied migration or local database was changed. Added both GET requests before logout in Postman.


### AngularJS frontend — 2026-10-25

- Implement all eight approved screens with AngularJS 1.8.3, Angular Material 1.2.5, local Roboto and Vite. Use browser-visible Figma screens, saved design-review images and the saved flows as the implementation references; connector extraction was unavailable. Preserve the navy navigation, blue actions, layout and currency-separated reporting. Desktop and mobile rendering were inspected.
- Keep the request client, formatting utilities, routed page controllers/templates and styles separate. Use same-origin session cookies, in-memory identity/CSRF, explicit request retries, structured errors and request IDs. Never store credentials or salary drafts in browser storage. Salary retry context survives inline current-record/history review while its page remains open; leaving/reloading that page discards this in-memory context.
- Initial auth/report browser checks were observed failing on missing UI before wiring the pages. The 29 API/format unit cases were developed test-first. Employee screen checks added after their initial implementation are regression coverage, not TDD evidence.
- Reproduced before fixing: stale authentication responses, unreadable 401 bodies, keyboard skip navigation changing routes, a fresh sign-in session notice, mobile directory overflow, history review erasing an uncertain salary operation, and enabled repeat inactivation while its request was pending. Corresponding unit/Playwright cases passed after the fixes. Uncertain salary retries retain the original serialized body and operation key; conflict review requires an explicit user action before a new save.
- Verification: 29 unit tests passed; 38 Chromium fixture/rendering checks passed with zero retries; the separate live-API Chromium workflow passed against a 20-employee in-memory H2 database. Live coverage includes sign-in, employee search/details, reference-driven country filtering, currency-separated reports, refresh and sign-out. `npm run build` passed. Employee writes, history and monthly estimates are covered with contract fixtures until those backend routes exist.
- Vite forwards `/api` locally. Production asset hosting is a separate deployment step because Spring currently protects its fallback non-API routes. No backend security configuration was changed for the frontend. Legacy dependency advisories and the Vite bundle-size warning remain open; no unsupported framework migration or forced dependency update was applied.

### Employee writes, salary history and monthly estimates

- Implement employee creation, complete details replacement and inactivation with dedicated JSON/request validation, typed records, service interfaces and SQL through SqlUtility. Body parsing rejects duplicate JSON keys, coercion, unknown/missing properties, unsupported encoding and bodies above 16,384 bytes. Creation stores the immutable salary baseline and authenticated actor snapshot together; database uniqueness controls concurrent ID/email collisions.
- Details/status changes take a database employee row lock and check the submitted shared version before no-op detection. Actual changes increment version once; current-version no-ops preserve version and timestamp. Employment-date edits must cover the initial and latest salary effective dates. Version conflicts include expected/current versions in the shared error envelope.
- Salary change execution uses a Spring transaction for employee salary/version, immutable history and actor-scoped replay metadata. SHA-256 fingerprints canonicalize employee ID, numeric amount, date, trimmed reason and original version. Check committed operations before current employee-state checks and again after acquiring the employee lock. Uniqueness-race recovery runs after rollback; equivalent retries return the committed original 201 body. Changed intent with an existing key returns 409. No independent connections or commits occur in utility calls.
- Add Liquibase YAML migration 012 for a stable public UUID on salary-change records; retain existing migration checksums and use the UUID in responses rather than exposing numeric keys. Capture actor display names in each history row.
- Monthly estimates read employment, baseline and effective salary from one SERIALIZABLE H2 transaction. Latest effective date within the month applies to the whole month; inclusive joining/leaving dates alone prorate pay. Return zero with null basis outside employment, 409 for uncovered eligible days, and round from annual salary once using HALF_UP. Estimates do not write data.
- TDD evidence: employee write (14), salary/history (10) and estimate (15) cases were observed failing against missing routes before implementation, then passed. Additional verification covers two employee-concurrency cases, two salary-concurrency cases and a real HTTP create/edit/salary/replay/history/estimate/inactivate/logout workflow. The full Maven clean verify passed 171 cases with zero failures/errors/skips and packaged the backend. All tests use isolated databases; the local persistent database was not modified by verification. Manual Postman/curl execution was not performed.


### AngularJS resource-URL policy — 2026-10-26

- CVE-2026-11998 concerns incomplete whole-URL matching when a trusted-resource regular expression contains alternation. The app previously used AngularJS’s default `['self']` origin comparison, without a custom regex matcher; the vulnerable regex configuration was not present. Source: [finder advisory and reproduction](https://www.herodevs.com/vulnerability-directory/cve-2026-11998).
- Set `$sceDelegateProvider.trustedResourceUrlList([])` in the application configuration. All current views and Material dialogs are bundled inline, so no uncached URL-based templates are needed. The empty list rejects untrusted Angular resource URLs, including same-origin URLs, without invoking regex matchers. API requests, normal links and static build assets keep their existing behavior.
- Do not put user-derived content into `$templateCache` or pass user-derived URLs to `$sce.trustAsResourceUrl`; these explicit trust mechanisms bypass the policy. This is application hardening, not a fix to AngularJS’s library implementation or a global browser/network filter. The dependency advisory remains open.
- TDD evidence: before the change, the three new Playwright checks produced one passing isolated vulnerability control and two expected failures: the app accepted a same-origin probe URL, and a compiled `ng-include` fetched its inert intercepted template. After the change, all three passed. Checks cover eight representative URL forms and verify no request or rendering for an unapproved same-origin template. The disposable control reproduces the regex bypass without changing the application injector or fetching a malicious URL; update that control if the dependency is replaced or patched.
- Final verification: all 41 Chromium frontend checks passed with zero retries, including existing filters, dialogs and pages; the separate real-API workflow also passed. All 29 unit tests and the production build passed. No backend files or dependency versions were changed for this mitigation.

### Directory UX revision — 2026-10-26

- Implement full-name prefix suggestions below the existing search box, debounced by 300 ms from one typed character. Display names only, without identifiers or helper text. Selection opens an employee profile; keyboard navigation, dismissal and stale-response handling are required. Suggestion failures must not disable the table. Preserve `q` substring semantics and existing API regression expectations. Exact behavior is in the [directory design revision](design/figma-ui.md#directory-revision).
- The frontend calls a separate authenticated `GET /api/employees/suggestions?prefix=R&limit=8`: trimmed prefix length 1–100, default limit 8 and maximum 10, literal case-insensitive full-name prefix matching with bound/escaped SQL values, and deterministic name/employee-ID ordering. Return only `employeeId` and `fullName`, keeping the ID internal for profile navigation. It was implemented after frontend commit `3baaafe`; the existing directory query contract remains unchanged.
- Apply changed table filters when focus leaves the whole control, including its popup; apply table search on blur or Enter, without an Apply filters button. Clear filters stays beside the dropdowns and acts immediately. Use fixed 100-row pages with an explicit frontend `size=100` and no size selector; retain Previous/Next, validated Go/Enter page navigation and explicit sorting. Restore search text, applied table query, filters, sort, page and scroll after employee review. The backend default size 25 and range 1–100 remain unchanged.
- Keep typed search separate from the applied table query. Store only directory controls and scroll in memory for the current authenticated identity; refresh employee data on return and clamp a page that no longer exists. Material popup focus restoration is checked before committing a filter. Reports retain their existing Apply filters behavior.
- TDD evidence: the fixed-page-size check first observed 25 instead of 100; initial suggestion checks observed missing lookup requests and options. The frontend now passes debounce, keyboard/click selection, stale-response, literal-prefix, failure isolation, whole-control blur, Clear, pagination, browser Back, scroll restoration and page-clamping checks. Existing API search/pagination regression expectations stay unchanged.
- Updated and visually reviewed the Figma directory and names-only suggestions preview; saved the [review image](design/review/directory-suggestions.png). The rendered frontend was also checked at desktop/mobile sizes and with names-only suggestions. The separate backend implementation and live suggestion integration were completed after the frontend commit.
- A focused red–green navigation check caught and fixed a blur race that removed an employee link before its click could open the profile. Primary pointer presses on profile links now preserve the draft until navigation; ordinary keyboard blur continues applying search.
- Frontend-stage verification: 16 directory checks, 41 existing Chromium checks, the existing isolated live-API workflow, all 29 unit tests and the production build pass. At that frontend checkpoint, live suggestions awaited the subsequent backend stage.
- Backend validation: new endpoint cases first failed because `/suggestions` resolved through employee-ID validation. After adding the explicit route, dedicated validator, two-field DTO and bounded parameterized query, all 19 suggestion cases and 20 unchanged directory cases passed. The isolated-H2 full `clean verify` passed all 190 tests with zero failures/errors/skips. Unicode boundary assertions were added as regression coverage after implementation. No schema migration or changes to existing `q` matching/default pagination were needed.
- Live integration: the new Playwright workflow first received 400 from the pre-change backend; after packaging the endpoint, both real-API workflows passed. It verifies the two-field response, names-only options, prefix selection, profile navigation, retained table state and the existing substring search. Final frontend coverage is 57 fixture/render/security checks plus 2 live workflows, with 29 unit tests and the production build also passing.

### Applied suggestion filters and multi-select focus — 2026-10-26

- Suggestions now receive the directory’s applied country, department, job-level and currency selections. Repeated values use OR within a category; categories and the name prefix combine using AND. Clear removes those filters from subsequent lookups. The backend validates the same canonical values and 20-value limit as the directory, deduplicates selections and applies bound SQL filters before the result limit. Existing table substring search and pagination are unchanged.
- TDD evidence: the new Playwright check first observed missing country parameters, then passed with all four categories, multiple values, out-of-filter decoys and Clear. The frontend change was committed first as `4c19eda`. Fourteen new isolated-H2 cases failed before backend implementation; afterward all 53 targeted suggestion/directory tests and all 204 backend tests passed. The real-browser filtered lookup initially returned 400 against the old backend; all three live Playwright workflows now pass against the rebuilt backend.
- Material retains a grey focused-option background after pointer selection. Scope the visual correction to multi-select menus: non-hovered rows return to white, while selection remains checked and keyboard focus uses an outline. Add regression checks across all four directory filters, the four report filters and report Group by, plus keyboard navigation.
- Final verification for this follow-up: all 68 fixture/render/security Playwright checks and all 3 live workflows passed, along with 29 unit tests, the production build and 204 backend tests. Nine pointer tests cover every multi-select filter; a tenth verifies the keyboard-active option via the listbox’s `aria-activedescendant` and its visible outline. The corrected keyboard test first failed for the missing outline, then passed after targeting the focused listbox’s active option.

### Salary-history baseline response — 2026-10-26

- The history response omitted the required `initialSalary` object even though the frontend and contract expected it. The original salary was still stored; the omission produced blank amount, effective date, recording time and creator fields. Existing browser fixture responses supplied that object, masking the live integration gap.
- Return the immutable baseline amount/currency/effective date on every history page, including empty, inactive and past-end pages. Read the original recording timestamp and creator-name snapshot from the employee, and expose the creator's public ID. Keep these reads in the existing serializable transaction. No salary data or database schema changes are required.
- TDD evidence: all four new isolated-H2 cases initially failed for missing `initialSalary`; the new live Playwright check displayed `— / year` instead of the seeded amount. The focused backend run passed all 29 cases after adding the response mapping. The browser regression also checks that a subsequent salary update changes the current salary while preserving the initial salary.
- Final verification: all 208 backend tests passed; the three existing live browser workflows passed and the new real-API history workflow passed after correcting its field selectors. The new workflow verifies initial salary, original effective/recording dates and creator, then saves a salary update in an isolated database and confirms the original baseline remains unchanged. The table layout and production salary records were not modified.

### Salary update field validation — 2026-10-26

- Validate annual base salary and effective date when focus leaves their fields, displaying linked inline errors without moving focus. Editing clears the affected error; Save revalidates both fields, including keyboard submission. Preserve invalid draft text so users can correct it.
- Require a positive plain-decimal salary with at most two fractional digits, retaining the backend's 15-digit integer limit and any stricter currency precision. Check real calendar dates, the latest salary date, today and employment bounds. Keep the backend as the authority for concurrent changes; accepting a refreshed conflict record revalidates the preserved draft, while uncertain retries keep their original request.
- TDD evidence: separate date and amount blur checks first failed because their inline errors remained hidden. All 22 new Playwright cases then passed, followed by all 90 frontend fixture/render/security checks, all 29 unit tests and the production build. No backend or database changes were needed.

### Workspace home link — 2026-10-26

- Make the signed-in sidebar's ACME logo/name a normal link to Employees, the existing authenticated landing page. Preserve its appearance and existing directory state restoration. Suppress the brand's hover underline while retaining its keyboard focus indicator.
- Two Playwright checks first failed for the missing link, then passed for desktop keyboard/click navigation and mobile navigation/menu closure. The production build and all four isolated live-API workflows passed after this change. Combined frontend coverage is 92 fixture/render/security checks plus four live workflows.

### Confirmation lifecycle and salary retry recovery — 2026-10-26

- Cancel a pending employee-inactivation confirmation when its detail component is destroyed. Check component liveness before opening the dialog and after confirmation so a departed page cannot submit a status update. The regression navigates Back with the dialog open, checks dismissal without a write, then returns Forward and confirms normally.
- Keep an unresolved salary operation's original payload and key when a later retry is rejected before replay lookup. A CSRF rejection clears the token through the existing API client; the next explicit retry obtains a new token and sends the same operation. Treat only service errors known to follow replay lookup as definitive rejection; generic validation can happen before that lookup. Initial definite failures still allow draft correction, and confirmed version conflicts require review before a new operation.
- TDD evidence: the dialog regression failed for the surviving modal; three salary cases failed for losing the original retry action after CSRF rejection, rate limiting and pre-replay validation. All three focused inactivation checks and six salary-recovery checks passed after implementation. The latter also cover unchanged body/key, token refresh, first-attempt rejection and definitive conflict/service-error recovery.
- Final verification: all 99 frontend fixture/render/security checks and four isolated live-API workflows passed, along with all 29 unit tests and the production build. No backend or schema changes were required; production employee records were not used for writes during verification.


### Mobile menu sign-out visibility — 2026-10-26

- The fixed mobile sidebar used `100vh` with a matching minimum height, allowing account actions to fall below the initial visible area on short screens. Use the dynamic viewport height with a `100vh` fallback, remove the minimum height on mobile and keep the account footer outside a separately scrollable navigation area. Include safe-area padding and a 44-pixel Sign out touch target. The wrapper preserves the existing desktop layout.
- TDD evidence: the new 390×400 and 568×320 checks failed before the fix because Sign out was below the viewport. They now verify visibility before any automatic scrolling, a stationary footer while navigation scrolls, and successful sign-out. A third check covers shrinking an already-open menu from 390×844 to 390×550.
- Verification: all 102 Chromium frontend fixture/render/security checks passed; all three mobile sign-out checks also passed in WebKit. Desktop and short mobile screenshots were inspected, and the production build passed with its existing bundle-size warning. Browser tests use fixture APIs; actual phone browser-toolbar behavior was not exercised.


### Validation across frontend flows — 2026-10-26

- Replace the misleading generic date-format error on Add/Edit with a neutral summary and explicit Employee ID guidance. IDs still require `EMP-` followed by exactly six digits: `EMP-09999` is invalid; `EMP-009999` is valid. The regression verifies correction without changing valid dates.
- Add field-by-field browser coverage for required inputs, whitespace, formats, reference selections, immutable fields, optional dates, length limits and corrections. Extend sign-in, salary reasons, monthly estimates, directory search/page inputs and report filtering/grouping; retain the existing salary amount/date, recovery and directory tests. Fixtures verify browser behavior; a separate real-backend suite verifies semantic dates, monetary limits, duplicate identity rejection and persistence.
- Reject year `0000` in the month input before requesting an estimate. Use one shared code-point length validator for employee names/job titles, salary reasons and passwords, matching the API limits; password whitespace remains significant. Preserve overlong drafts and show a length error. Directory search keeps its existing 100-character cap while counting supplementary Unicode correctly.
- TDD evidence: the ID-message regression, year-zero month case, ten name/title/reason length cases, and three password/search boundary cases failed before their fixes. The remaining added tests cover already-implemented behavior and are regression coverage.
- Frontend verification: all 168 Chromium fixture/render/security checks, 29 unit tests and the production build passed. The 18 cross-flow input cases passed in Chromium, Firefox and WebKit; focused name/title/reason cases passed in Chromium and WebKit. Eight accessibility/navigation checks passed in WebKit after adapting the existing keyboard test to macOS Option+Tab for links; comparison against the original DOM confirmed that ordinary Tab skipping links predated the sidebar change.


### Unicode database capacity — 2026-10-26

- The real-browser Unicode boundary workflow exposed a storage error after valid 200-code-point names/job titles passed request validation. H2 counts `VARCHAR` capacity in UTF-16 units, so supplementary characters require two units each; the same mismatch affected 1,000-code-point salary reasons. Add migration `013` widening only those three columns to 400/400/2000 units. Keep API limits and previously applied changesets unchanged.
- Three of nine new isolated-H2 regressions failed before the migration; all nine passed afterward. They cover create/edit persistence, salary reason persistence and replay, plus unchanged rejection above the public limits. Full Maven `clean verify` passed 227 tests with no failures/errors/skips and packaged the backend, including the existing upgrade/restart and checksum checks. The running backend and persistent local database were not changed; migration `013` applies on the next backend restart.
- Final real-browser verification passed all 26 live workflows, including the 22 new validation cases and exact persistence of 200-code-point names/job titles and a 1,000-code-point salary reason. A copied verified JAR and temporary ports 19080/5274 isolated this run from concurrent builds/tests using the normal test ports; its database remained in memory.

## Coverage gaps and GitHub Actions builds — 26 October 2026

Added regression coverage for existing behavior: `SalaryRollbackTests` forces real H2 constraints to fail at history insertion and at the subsequent employee update. There is no test-managed transaction; separate reads verify unchanged employee data, version and history after application rollback. Retrying the failed key creates exactly one change and then replays it. The implementation inserts history first, so failing the employee update is the case that proves an already-executed write rolls back.

`SnapshotConsistencyTests` uses a SQL spy only to pause the reader after its first real query. A separate real transaction commits salary/employment changes or report salary/department/population changes before the reader continues. Assertions require a complete old snapshot and independently verify that a later request sees the committed new state. Latches have bounded waits; no sleeps or mocked database results are used. Base-pay API additions verify leap/non-leap February, 30/31-day single-day employment and recalculation after a backdated save. These tests are regression coverage, not a claim of test-first development for existing services.

JaCoCo 0.8.15 now instruments the backend while retaining Mockito's existing agent via late `argLine` evaluation. Maven `verify` requires 80% lines and 70% branches across all backend classes. Native Node coverage requires 95% lines, 85% branches and 85% functions across frontend services/utilities/validators, including unloaded files in those directories. Page controllers/templates/styles remain outside this unit-coverage percentage. Sources: [JaCoCo agent configuration](https://www.jacoco.org/jacoco/trunk/doc/prepare-agent-mojo.html), [Node coverage](https://nodejs.org/learn/test-runner/collecting-code-coverage).

Sixteen committed Chromium screenshot comparisons cover eight screens at desktop/mobile widths. CI uses macOS 15 to match the baseline platform, allows 1% differing pixels, and disables retries. The first full three-engine run observed four failures: Firefox lacked the focused option outline and clipped Reports in the short landscape menu; WebKit submitted the abandoned search on Clear filters in two scenarios. Fixes use focused-listbox styling, smaller branding margins in short mobile menus, and prevention of pointer-induced focus changes before Clear executes. Existing regression tests then passed: 66 focused Firefox/WebKit checks, followed by all 504 fixture checks across three engines. The baseline comparison rerun passed without regenerating images after these fixes.

Local final verification used Java 21.0.13, Node 26.9.0 and disposable H2 databases: Maven `verify` passed 227 tests (zero failures/errors/skips), with 90.58% line and 81.19% branch coverage; `npm run test:coverage` passed 32 tests with 96.91% lines, 88.67% branches and 90% functions in its stated scope. The live browser suite passed 26 checks. The frontend production build passed, retaining the existing >500 kB chunk warning. These totals include concurrent workspace Unicode validation/storage changes; those changes are not attributed to this coverage increment.

`.github/workflows/ci.yml` runs on push, pull request and manual dispatch with read-only repository permissions. It verifies tests/coverage, all three browser engines and live workflows before retaining the backend JAR/frontend assets; failure reports and coverage outputs are retained for 14 days. The user requested build/test only, so no deployment is configured. Workflow YAML was parsed locally and its commands were exercised locally. GitHub-hosted runner execution remains unverified until the workflow is committed/pushed and run. No commit, push or deployment was performed by this increment.
