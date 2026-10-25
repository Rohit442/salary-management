# Testing Strategy and Verification Plan

This plan supports the [requirements](requirements.md), [business rules](requirements-rules.md), and [technical design](technical-design.md). It defines the development workflow, test boundaries, and acceptance checks.

## TDD workflow

Use test-driven development for core business behavior and bug fixes:

1. Identify a small observable behavior and independently determine its expected result.
2. Write one test and run it. Confirm failure for the intended missing/incorrect behavior; setup or dependency failures do not prove the requirement is tested.
3. Implement the smallest production change that satisfies the example.
4. Run the relevant tests, then refactor code and tests while preserving behavior and passing results.
5. Add the next example or edge case and repeat. Keep commits incremental and leave shared/releasable commits green.

Tests written after a completed feature are regression tests, not evidence of test-first development. For bugs, first reproduce the failure with a regression test. Do not create tests for trivial accessors or implementation details simply to increase coverage. Reversible documentation and visual-only edits need appropriate review rather than artificial unit tests.

## Test boundaries

| Level | Scope and tools | Boundary |
|---|---|---|
| Unit | JUnit, parameterized examples, focused domain objects; Mockito only where useful | No Spring context, network, or H2 for pure rules/calculations |
| HTTP/security | Spring MVC/security test support | Request mapping, JSON precision, authentication, HR access, CSRF and error format |
| H2 integration | Spring test support with isolated in-memory H2 and temporary file databases | Real migrations, constraints, aggregates, transactions, optimistic locking and retries |
| Browser | Playwright against the running UI/API and isolated test data | Essential HR journeys, persisted outcomes, keyboard interaction and recovery feedback |

Test the behavior at the lowest level that provides meaningful confidence. Use integration tests where a unit test cannot establish correctness, especially for transaction rollback and database concurrency. Keep browser tests focused on journeys instead of repeating every validation permutation through the UI.

Prefer constructor-injected dependencies, explicit test fixtures, fixed clocks/IDs, and Arrange–Act–Assert structure. Assert outcomes, not incidental private method calls. Avoid unnecessary mocking; mocked repositories cannot prove H2 correctness. Use synchronization barriers for race tests rather than timing-dependent sleeps. Isolate records between tests and avoid dependence on execution order.

## Acceptance examples

| ID | Observable requirement | Primary check |
|---|---|---|
| SAL-01 | Reject zero/negative amounts and preserve stored data | Parameterized unit test; one API rejection/persistence check |
| SAL-02 | Reject unsupported currency precision and missing/blank reasons | Unit tests using the configured currency definitions |
| SAL-03 | Commit salary and history together or neither | H2 test that fails history insertion after the salary write; verify committed state through a separate transaction |
| SAL-04 | Reject salary changes for inactive employees | Service test and H2 status/salary race test |
| CON-01 | Two edits using the same version cannot both succeed | Independent H2 transactions; assert one winner, one conflict, and consistent history |
| RET-01 | Retry the same salary operation without duplicate history | Sequential and concurrent H2 requests using the same Idempotency-Key |
| RET-02 | Reject a committed Idempotency-Key reused with a changed payload | Integration test; assert no additional write |
| RET-03 | Replay a committed outcome after a later salary/status/actor-name change | H2 integration test; stable history body and fresh tracing ID |
| SAL-05 | Reject unchanged proposed salary without new history/version | Unit/H2 checks; committed replay bypasses this new-operation check |
| EMP-01 | Reject duplicate IDs and normalized emails, including inactive records | H2 constraints and API error mapping |
| REP-01 | Apply AND across filters and OR within a multi-select filter | Known-value query fixtures |
| REP-02 | Return correct count, sum, mean and odd/even median | H2 query tests and independently calculated expected values |
| REP-03 | Exclude inactive records, separate currencies, handle no matches | H2 report fixtures and one browser report journey |
| REP-04 | Keep summaries/groups consistent and allow totals above an individual salary cap | Concurrent snapshot and large-decimal H2 tests |
| PAY-01 | A raise on October 15 applies to all October employment days | Independent salary timeline fixture; October uses the new rate, September uses the old rate |
| PAY-02 | Joining/leaving dates are inclusive and use actual month length | Leap February, 30/31-day months, same-day employment, full and zero months |
| PAY-03 | Choose the latest effective date within a month, including multiple raises | H2 timeline query; backdated saves recompute estimates; audit order differs from effective order |
| PAY-04 | Preserve estimate access after inactivation and reject incomplete coverage | API/H2 tests; future end date and future-month projection |
| PAY-05 | Calculate from one snapshot and round only the final amount | Concurrent salary/date writes; independently calculated fractional fixtures |
| REF-01 | Return values/precision consistent with backend validation | API and H2/configuration integration checks |
| SEC-01 | Reject unauthenticated/non-HR access and invalid CSRF; invalidate logout sessions | Spring security/HTTP tests |
| UI-01 | Find an employee, update salary, refresh, inspect recorded history | Playwright with backend and database assertions through supported interfaces |
| UI-02 | Create/edit an employee and confirm inactive status | Playwright with unique fixture identities |
| UI-03 | Preserve inputs on failure, explain conflicts, and support keyboard use | Focused browser tests using deterministic failure/conflict setup |

Use an explicit failure seam or a real constraint failure to test rollback. Let the application transaction reach its commit/rollback boundary; a test framework's automatic rollback is not proof that the application's transaction works.

## Independent reporting fixtures

For five active INR salaries of 1,200,000; 1,320,000; 1,440,000; 1,560,000; and 1,680,000, expected count is 5, total is 7,200,000, and mean/median are both 1,440,000. For two salaries of 1,200,000 and 1,680,000, expected median is 1,440,000.

Add an inactive employee and a different-currency employee to verify exclusion and separation. Include a rounding case whose average/median has more decimal places than the currency displays, and assert HALF_UP only at the display boundary. Never generate expected values by calling the production aggregation function.

For monthly base pay, use annual INR 1,200,000 from April 1, 2026, raised to INR 1,800,000 effective October 15, with employment ending November 12. Expect September INR 100,000, all October INR 150,000, November INR 60,000 (12/30), and December zero. For annual `100.00` and 29/30 eligible days, require `8.06`; premature monthly rounding would yield `8.05`.

## Continuous integration and reproducibility

Configure a pipeline after the application build exists. A clean checkout must use pinned tools, the Maven Wrapper, frontend lockfiles, versioned migrations, and isolated fixtures. Run fast unit/HTTP checks early, then H2 integration and essential browser checks before release. Publish failures and suite durations. Keep credentials out of source and logs.

Maintain fast feedback and investigate slow suites. Do not hide failures behind unconditional retries or label a flaky test healthy because a rerun passed. Record any environment-blocked check as blocked, with its reason; it is not a pass. Add verified test commands to the README during implementation.

## Performance and operational evidence

Measure directory search, pagination/count queries, and report combinations against the repeatable 10,000-employee seed. Record environment, H2/application versions, resource limits, active/inactive distribution, request mix, concurrency, warm-up, sample size, p50/p95/p99 latency, throughput, and error rate. Inspect slow-query plans and connection-pool saturation. Do not invent throughput from employee count or adopt an unmeasured latency claim.

Verify that health/readiness and correlation IDs help diagnose failures while operational endpoints remain protected. Observe latency, traffic, errors, and relevant resource saturation. Check that logs exclude credentials, session identifiers, salaries, and change reasons. Introduce caching, replication, queues, or scaling only in response to measured needs described in the technical design.

## Completion evidence

For each verified change, retain its behavior/test ID, commit or CI reference, environment, executed command, actual outcome, and known gaps. Record red/green evidence when claiming TDD, and update the [engineering log](engineering-notes.md) with significant decisions and corrections. Keep observed results separate from this planned checklist.

AI may help propose edge cases, test scaffolding, or refactorings. Review assertions against the requirements and independent expected values, run them, and inspect failures. Generated tests and generated production code agreeing with each other are insufficient evidence when both can share the same misunderstanding.

## References

- [TDD and red–green–refactor](https://www.martinfowler.com/bliki/TestDrivenDevelopment.html)
- [Practical Test Pyramid](https://www.martinfowler.com/articles/practical-test-pyramid.html)
- [DORA test automation](https://dora.dev/capabilities/test-automation/) and [continuous integration](https://dora.dev/capabilities/continuous-integration/)
- [AWS safe retries and idempotency](https://aws.amazon.com/builders-library/making-retries-safe-with-idempotent-APIs/)
- [Google SRE monitoring signals](https://sre.google/sre-book/monitoring-distributed-systems/)

## Authentication API initial red-phase coverage — 25 October 2026

The current test-first suite is `backend/src/test/java/com/acme/salarymanagement/rest/AuthenticationApiContractTest.java`, derived from `api-auth.md`. It uses the real security chain and an isolated H2 fixture; HTTP/session assertions are integration tests, not database-free unit tests.

| Endpoint | Core contract cases |
|---|---|
| `GET /api/auth/csrf` | Anonymous token/session issuance, token/header fields, login token rotation, shared response headers |
| `POST /api/auth/login` | HR JSON response, email normalization, password whitespace significance, generic credential failures, required/duplicate/unknown fields, password length, JSON rejection, CSRF, attempt limits and success reset |
| `GET /api/auth/me` | Current user response, anonymous rejection, fresh enabled-status check and revoked-session invalidation |
| `POST /api/auth/logout` | Session invalidation, expired cookie, bodyless 204, anonymous logout, CSRF rejection without losing the current session, old-token rejection and refreshed-token success |
| All four | Unsupported-method JSON errors and `Allow`; response/error helpers verify correlation IDs, no-store, no redirects, and exclusion of sensitive response fields |

`HrUserDetailsServiceTest` and `LoginFailureHandlerTest` add 14 isolated regression cases for existing email normalization, result mapping, enabled flags, missing accounts, database-outage propagation and generic browser failure handling. They need no Spring context/database and do not represent retroactive TDD for the already implemented browser login.

Observed `mvn test`: 59 cases, 30 passing, 29 expected contract failures, zero test errors or skips. The API routes remain unimplemented. Additional transport/container details such as raw malformed form parsing, session cookie attributes and timed expiry require focused tests as those behaviors are introduced; this core suite is not a claim that every contract branch is covered.

## Authentication test results — 25 October 2026

This is the baseline snapshot before the CSRF implementation. The later CSRF increment records the current result below.

A test passes when all assertions for its behavior match the actual result and the test completes without failure/error. Assertions check observable results such as normalized email, enabled flags, generic failure messages, HTTP responses, or persisted rows. Passing a test for existing code does not establish that a new API is implemented.

| Test group | Cases | Observed result | What it verifies |
|---|---:|---|---|
| `HrUserDetailsServiceTest` | 9 | Pass | Existing email normalization, stored-user mapping, disabled flags, missing accounts, database-outage propagation; SQL dependency/result rows are mocked |
| `LoginFailureHandlerTest` | 5 | Pass | Existing browser sign-in failure redirect and generic failure message; uses mock servlet requests/responses |
| Existing regression suites | 16 | Pass | Existing Spring browser login, H2 console/security, Liquibase schemas and constraints, seeding, upgrades, and persistence |
| `AuthenticationApiContractTest` | 29 | Fail | New JSON auth APIs: `/api/auth/csrf`, `/api/auth/login`, `/api/auth/me`, `/api/auth/logout`; routes/required JSON behavior are absent |
| **Total** | **59** | **30 pass, 29 fail** | **The full test run fails; zero new API cases pass** |

The 14 isolated unit tests exercise existing Java components. They start neither Spring nor H2 and do not prove API routing, database queries, or complete authentication/session behavior. The 16 existing regression tests cover the implemented bootstrap/browser/database behavior. These 30 passes are independent of the missing JSON API implementation.

### Commands actually executed

The working directory was `/Users/rohit/Personal projects/salary-management/backend`. Execution used IntelliJ's bundled Maven and JDK 25, with the project's Java 21 compilation target. The command prefix was:

```sh
JAVA_HOME='/Applications/IntelliJ IDEA.app/Contents/jbr/Contents/Home' \
  '/Applications/IntelliJ IDEA.app/Contents/plugins/maven-plugin/lib/maven3/bin/mvn'
```

That prefix was used for these Maven arguments:

```text
-B -Dtest=HrUserDetailsServiceTest,LoginFailureHandlerTest test
-B -Dtest=AuthenticationApiContractTest test
-B test
```

The first run passed all 14 unit cases. The second failed all 29 API contract cases. The final full run reported:

```text
Tests run: 59, Failures: 29, Errors: 0, Skipped: 0
BUILD FAILURE
```

This records the TDD red stage, not completed API functionality. Implementation must make the contract cases pass while retaining the existing regression passes. Tests added to already implemented components are regression coverage rather than evidence that those components were developed test-first.

## CSRF API first increment — 25 October 2026

The approved first increment implements `GET /api/auth/csrf`, with a dedicated validator, service interface/implementation, typed responses, API-only security chain, header-only CSRF resolution, shared JSON errors, server request IDs and no-store headers. Spring owns session-backed token generation. Existing browser sign-in and H2 console security retain their separate chains.

Observed red: the original anonymous-CSRF contract case returned 401 rather than 200. Eight new request/token cases also failed before implementation. The focused implementation run then passed those eight cases and the original case. A further saved-request/session regression check was added, producing nine passing `CsrfApiTests` cases.

The CSRF suite verifies anonymous session/token association without authentication, session reuse, client request-ID replacement, query/body rejection, JSON negotiation, unsupported methods/Allow, CSRF rejection precedence, actual issued-token acceptance in a header, rejection of token form parameters, and absence of saved-request sessions for unauthenticated API requests. The shared JSON error writer formats failures from both the MVC and security-filter paths. The browser-session fixture now asserts successful authenticated HR identity, and the test profile explicitly selects demo seed data.

Final `mvn -B test`: 68 cases, 43 passing, 25 failures, zero errors/skips. The full build remains failed. Breakdown:

| Group | Passing | Failing |
|---|---:|---:|
| Existing regression cases | 30 | 0 |
| `CsrfApiTests` | 9 | 0 |
| `AuthenticationApiContractTest` | 4 | 25 |

The four green auth contract cases are anonymous CSRF issuance, unsupported-method rejection for the CSRF path, anonymous `/me` rejection, and missing-CSRF rejection before login. The latter checks prove shared guards, not successful login/current-user/logout functionality. Those three implementations remain pending. Raw transport/cookie/expiry checks still require their planned embedded-server coverage; no end-to-end completion is claimed.

Focused command arguments used with the recorded IntelliJ Maven/JDK prefix:

```text
-B -Dtest=AuthenticationApiContractTest#csrfIsPublicAndCreatesAnAnonymousSessionWithAnOpaqueToken test
-B -Dtest=CsrfApiTests test
-B -Dtest=CsrfApiTests,AuthenticationApiContractTest#csrfIsPublicAndCreatesAnAnonymousSessionWithAnOpaqueToken test
-B test
```

## Remaining authentication APIs: green increment — 25 October 2026

The current JSON auth implementation is complete for the four documented routes. Focused tests first reproduced missing login/current-user/logout behavior (401/404 instead of 200/204). New isolated tests reproduced unimplemented rolling-window behavior and BCrypt's inability to encode 128-character passwords before those implementations were added.

The final local `mvn -B clean verify` used IntelliJ's bundled Maven/JDK, the Java 21 target, isolated H2 fixtures and random-port embedded HTTP servers. Result: **81 tests, zero failures/errors/skips; BUILD SUCCESS**, including all 29 original contract cases, nine CSRF checks, five password/limiter cases and eight additional real HTTP cases. The remaining cases are the existing regressions. The packaged application was produced; no commit was made.

HTTP coverage verifies the complete CSRF/login/refresh/me/logout flow, real session-cookie creation/rotation/deletion, local HttpOnly/SameSite flags, rejection of stale cookies/tokens, validation before logout cleanup, raw malformed/duplicate fields, and declared/chunked body bounds. Limiter expiry/rounding/pair isolation uses an injected clock without sleeps. Actual timed servlet expiry and an HTTPS deployment were not exercised by this run; the configured timeout and production cookie setting remain explicit.
