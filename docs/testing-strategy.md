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
