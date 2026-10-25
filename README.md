# ACME Salary Management

## Project status

Requirements, API contracts, technical design, and eight Figma screens are available. Application implementation, Oracle provisioning, application tests, CI, and deployment are pending. The current Playwright starter verifies browser setup.

Feature exclusions are defined in [Out of scope](docs/requirements.md#out-of-scope).

## Planned application stack

Java with Spring Boot, Spring Data JPA, Spring Security, AngularJS, and **Oracle Database**, using versioned Oracle SQL migrations. Local database development will use Oracle Database Free. See the [product requirements](docs/requirements.md) and [Oracle database setup guidance](docs/database-oracle.md).

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
- [Testing strategy and verification plan](docs/testing-strategy.md), including TDD, Oracle checks, CI and measurement evidence
- [Oracle database setup and decision](docs/database-oracle.md)
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
