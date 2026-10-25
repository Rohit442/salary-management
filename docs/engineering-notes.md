# Engineering Decisions and AI Usage

This log records adopted engineering decisions, their rationale, and supporting evidence.

## Decisions

| Decision | Reason |
|---|---|
| Manage annual base salaries and monthly base-pay estimates | Cover compensation maintenance and joining/leaving calculations |
| Keep monetary reports separated by currency | Avoid unsupported exchange-rate assumptions and misleading totals |
| Use Java and Spring Boot | Match the user's chosen ecosystem |
| Bootstrap with Java 21 and Spring Boot 3.5.16 | Pin the backend baseline in the Maven POM; use Boot dependency management for compatible Spring, Oracle, Flyway, and test versions |
| Use AngularJS | Follow the assignment's Java-specific frontend wording |
| Use Oracle Database | User explicitly chose Oracle after comparing database options |
| Build a modular monolith | Keep transactions, deployment, and internal responsibilities manageable |
| Use pre-provisioned HR accounts and server sessions | Support named HR identities and server-managed sessions |
| Save salary and audit history atomically with version checks | Preserve history consistency and prevent silent overwrites |
| Retain eight main screens | Cover employee maintenance, salary history, and compensation reporting |
| Use TDD for core behavior and bug fixes | Drive design with observable examples and fast feedback; refactor safely |
| Use focused units and constructor injection | Keep business behavior independently testable and dependencies explicit |
| Verify database behavior against Oracle | Validate actual constraints, transactions, queries, and conflicting writes |
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

The Oracle decision supersedes the earlier PostgreSQL choice.

## Engineering practice and evidence

Use the [testing strategy](testing-strategy.md) to apply red–green–refactor, select meaningful unit/integration/browser boundaries, and verify transactional and retry behavior. CI, observability, and deferred scaling concepts are defined in the [technical design](technical-design.md). Do not add infrastructure solely to demonstrate a pattern.

For an implemented behavior, record its test identifier, expected failing result, passing result after implementation, and relevant refactoring. Use actual command/CI output or commit references where available. Coverage percentages alone do not demonstrate correctness or TDD.

Reference guidance: [TDD](https://www.martinfowler.com/bliki/TestDrivenDevelopment.html), [test boundaries](https://www.martinfowler.com/articles/practical-test-pyramid.html), [continuous testing](https://dora.dev/capabilities/test-automation/), [safe retries](https://aws.amazon.com/builders-library/making-retries-safe-with-idempotent-APIs/), and [operational signals](https://sre.google/sre-book/monitoring-distributed-systems/).

## AI-assisted work and verification

| Work | AI contribution | Review and evidence |
|---|---|---|
| Requirements | Drafted and refined scope, exclusions, and correctness rules | User supplied the baseline, approved refinements, and selected Oracle; saved in [requirements](requirements.md) and [rules](requirements-rules.md) |
| UI planning and design | Converted supplied flows into FigJam diagrams; created eight editable Figma screens and supporting states | Main screens and supporting states reviewed; effective dates and monthly estimate designs updated, with manual layout/recovery repairs recorded in the [Figma handoff](design/figma-ui.md) |
| Design standards | Reviewed the supplied standards and produced an enhanced PDF | Generated PDF pages visually inspected; [saved standards](../output/pdf/salary-management-design-standards-v2.pdf) |
| API contracts | Added employment dates, salary effective dates/baseline, and the monthly base-pay endpoint | Validated JSON examples, shell/heredoc syntax, local links, endpoint/curl coverage, and independently checked example arithmetic; [API index](api-conventions.md) |
| Technical design | Proposed architecture, data model, transaction flow, and verification approach | Checked local links and the presence of three Mermaid blocks; [technical design](technical-design.md) |

Representative requests were “apply refinements,” “making the UI screens on figma,” and “Switch to Oracle SQL.” Retain relevant implementation prompts and verification evidence as development proceeds.

Add observed implementation results and significant decision changes to this log as work occurs.
