# ACME Salary Management — Requirements Rules and Decisions

This document defines the business rules supporting the [requirements](requirements.md).

## Employee identity and status

- Employee ID and work email are required and unique across active and inactive employees. Trim surrounding whitespace; compare email addresses case-insensitively. Enforce uniqueness in the database as well as application validation.
- Employee ID is immutable after creation. Other required fields follow the schema listed in the requirements.
- Replace complete editable details through a versioned PUT on the details subresource. Set inactive status through a separate versioned PUT; reject partial details and changes to salary/currency through these operations. Request schemas, field limits, initial reference values, and retry handling are defined in the [employee API contract](api-employees.md).
- Marking an employee inactive preserves their record and salary history and removes them from active-employee compensation reports. Inactive records remain searchable and readable; salary updates are rejected.

## Money and salary updates

- Store and calculate amounts using decimal arithmetic, not binary floating point. Require amounts greater than zero and reject unsupported currencies and excessive fractional precision rather than silently rounding submitted values.
- Maintain an explicit supported-currency list and the accepted decimal precision for each. Seed data, validation, formatting, and reports use the same definitions.
- Keep an immutable initial salary amount/effective date and append effective-dated changes in chronological order. A mid-month change applies to that entire month; multiple changes in the same month use the latest effective date. Recording timestamps remain separate. See [salary rules](api-salaries.md#effective-date-and-monthly-application).
- Set an employee's salary currency at creation. Salary updates use the employee's existing currency.
- Require a nonblank reason for every salary update. Capture the authenticated actor and a server-generated timestamp. Display timestamps with an explicit timezone.
- Reject a proposed amount numerically equal to the current salary; do not increment version or create history. Check replay of a committed operation before applying this new-operation rule.
- Commit the salary update and its history entry in one database transaction. Failed validation or persistence leaves both unchanged. History is read-only through the application.
- Show current and proposed amounts, absolute change, and percentage change before saving. Disable repeated submission while saving; retain inputs on failure.
- Use record versions or equivalent optimistic concurrency control. A stale edit must fail without overwriting a newer record; show the conflict, preserve the draft, and require review of the latest values before resubmission. Reconcile uncertain save outcomes before retrying to avoid duplicate audit entries.
- Salary operations use a client-generated `Idempotency-Key`, distinct from the HTTP tracing ID. Preserve its committed outcome and actor display-name snapshot with history; [salary API rules](api-salaries.md) define replay, conflicts and retention.

## Monthly base pay

- Store required joining date and optional inclusive last employment date. Inactivation preserves these dates; a future end date does not automatically inactivate a record.
- Use the latest salary effective on or before the selected month's end. A mid-month raise applies to the entire month. Use the latest recorded rate for a future month and label the result an estimate.
- Count the intersection of the calendar month and inclusive employment interval. Calculate `annual salary × eligible calendar days / (12 × days in month)` using exact decimal/rational arithmetic; round only the final amount using the currency precision and HALF_UP.
- Preserve an immutable salary baseline. If eligible days begin before the baseline's effective month, report incomplete salary history instead of assuming the current salary or zero.
- Estimates remain available for inactive employees and use employment dates rather than report status. A month without employment returns zero with zero eligible days.
- Follow the [monthly base-pay contract](api-base-pay.md) for request, response, date, and coverage rules.

## Reporting definitions

- Include active employees only. Apply different filter categories with AND semantics; if a category supports multiple selected values, match any selected value within that category.
- Group by the selected dimensions and always partition monetary results by currency. Comparisons at the same job level must also use the same currency.
- Count is the number of matching employees in each group. Total is the sum of annual base salaries; average is total divided by count.
- Sort salaries numerically to calculate median. For an odd count, use the middle amount; for an even count, average the two middle amounts.
- Retain sufficient decimal precision for calculations. Round displayed monetary metrics to the currency's configured precision using HALF_UP consistently; never calculate aggregates from previously rounded display values.
- An empty result shows no matching data and count zero. Average and median are unavailable, not zero. Do not display a currency-free monetary total across currencies.
- The [report API](api-reports.md) returns effective filters, groups, per-currency summaries and cross-currency employee count from one consistent snapshot. Reference options and precision come from the [reference-data API](api-reference-data.md).
- Default history ordering is newest first with a stable tie-breaker. Preserve directory filters and page position when returning from an employee record; reset pagination when search or filters change.

## Framework decision

The selected frontend is **AngularJS**, matching the assessment's Java-specific wording. AngularJS and modern Angular are distinct frameworks; do not substitute one silently. Record the exact selected versions, component library, compatibility constraints, and trade-offs in the architecture notes before implementation.

## Database decision

Use **H2** with **Liquibase** for schema migrations and seed data. Local runs use a persistent file; integration tests use isolated in-memory or temporary file databases. No separately installed database server is needed.

- Use Spring-managed JDBC/JPA connections and transactions.
- Represent money with Java `BigDecimal` and fixed-precision `DECIMAL(19,4)` columns; validate currency precision separately.
- Enforce identity uniqueness, normalized work-email uniqueness, relationships, and positive amounts in the schema.
- Keep salary and history writes in one transaction, with optimistic concurrency checks.
- Verify H2 pagination, median, decimal aggregation, rounding, dates, and concurrent writes against deterministic fixtures.
- Apply the 10,000-employee Datafaker seed once through Liquibase on first run. Use small, separate fixtures for normal tests; ordinary restarts reuse persisted data.

See [H2 database setup](database-h2.md).

## Verification scenarios

- Exercise search, filtering, and pagination against all 10,000 seeded employees, including mixed currencies and long employee names.
- Verify unique ID/email rejection, immutable IDs, nonpositive amounts, unsupported currencies, excessive precision, and missing reasons.
- Verify salary/history atomicity, inactive-employee restrictions, stale-edit rejection, and duplicate-save prevention.
- Reject unauthenticated access at backend endpoints; UI hiding alone is insufficient.
- Check odd/even medians, one-employee groups, empty groups, decimal rounding, combined filters, inactive exclusion, and currency separation using known expected values.
- Cover sign-in, employee lookup, salary update/history, employee maintenance, and filtered reports in browser tests. Check keyboard use and loading, empty, failure, retry, and success feedback.

## Delivery boundary

Keep the eight planned screens: Sign in, Employee directory, Employee details, Salary history, Update salary, Add employee, Edit employee, and Compensation reports. Add/Edit share a form layout. Mark inactive uses a confirmation dialog; feedback states do not require additional screens.

Verify repository history, tests, deployment, and the demo against the delivery requirements before submission.
