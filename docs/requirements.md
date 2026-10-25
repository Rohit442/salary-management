# ACME Employee Salary Management — Requirements

**Primary user:** HR Manager  
**Organization:** 10,000 employees across multiple countries

## Goal

Replace fragmented Excel spreadsheets with a web application that lets HR maintain employee salary data and answer compensation questions across the organization.

## Scope and Features

**Employee directory:** Search by name or employee ID; filter by country, department, job level, and currency; and navigate paginated results. Records contain employee ID, name, work email, country, department, job title, job level, employment status, joining date, optional last employment date, annual base salary, salary effective date, and currency.

**Salary maintenance:** Create employees, edit details, update salaries with a required reason and effective date, and mark employees inactive. Require unique employee IDs and work emails, immutable employee IDs, supported currencies, and positive salary amounts with currency-appropriate precision. Save each salary update and its history atomically, recording actor, recording timestamp, effective date, previous amount, new amount, currency, and reason. Preserve the initial salary amount and effective date as a baseline. Reject conflicting edits instead of silently overwriting changes. Preserve inactive records and history; exclude them from reports and prevent salary updates while inactive.

**Compensation reporting:** Show active employee count, total annual base salary, average, and median, with filtering and grouping by country, department, job level, and currency. Support questions such as “What is our salary commitment in India?” and “How do salaries differ across departments at the same job level?” Apply filters consistently, calculate using unrounded decimal values, and round only for display. Keep monetary metrics separated by currency; never sum or directly compare different currencies. Label amounts as annual base salary.

**Monthly base pay:** Show annual salary divided by 12 and a monthly estimate based on the salary applicable to the selected month. A mid-month raise applies to the whole month; use the latest effective date within that month. Prorate only for joining or leaving, using actual calendar days and inclusive employment dates. Preserve historical salary selection and support estimates for inactive employees.

**Access and usability:** Restrict the interface and backend endpoints to authenticated HR users. Support keyboard navigation for core workflows and provide validation, loading, empty, failure, and success feedback. Preserve entered values after failed saves or conflicts.

## Technical and Delivery Requirements

- **Stack:** Java with Spring Boot, Spring Web, Spring Data JPA, and Spring Security; AngularJS with a suitable component library; Oracle Database with versioned schema migrations written in Oracle SQL. Document framework versions and the AngularJS choice separately.
- **Data:** A repeatable seed script creating 10,000 fictional employees across countries, currencies, departments, and job levels.
- **Engineering and verification:** Use TDD for core business behavior and bug fixes, with red–green–refactor cycles. Maintain focused, testable code; fast, deterministic unit tests; Oracle integration tests for persistence, access control, and concurrency; and Playwright tests for essential workflows. Automate build/test checks and record measured performance.
- **Delivery:** A functional deployment, setup and test instructions, a video demo, and a Git repository with incremental commits. Commit this document before application implementation and include design/trade-off notes and evidence of intentional AI use and verification. Submit the repository link by replying to the assessment email.

## Out of scope

This section defines feature exclusions for the initial release across the requirements, design, and API contracts.

| Features | Rationale |
|---|---|
| Payment disbursement, payslips, taxes, deductions, net pay, bonuses, benefits, and equity | Focus on salary maintenance, base-pay calculations, and compensation reporting. |
| Currency conversion and employee salary-currency reassignment | Keep salary changes and monetary comparisons within one currency. |
| Historical trend dashboards and scheduled or future-effective salary changes | Retain effective-dated salary history for monthly estimates; enter changes effective today or earlier in chronological order. |
| CSV import/export, approval workflows, employee self-service, and external integrations | Prioritize complete salary maintenance and reporting workflows. |
| Public registration, self-service password recovery, and SSO integration | Use operator-provisioned HR accounts with documented credential reset. |
| Employee deletion, reactivation, and editing or deleting salary history | Preserve employee records and an immutable audit trail. |
| Reference-data administration UI and write APIs | Maintain supported values through versioned project configuration. |

## Acceptance Criteria and Assumptions

Against the 10,000-employee dataset, an authorized HR manager can find, create, and edit employees; update salaries with a reason; inspect history; estimate monthly base pay using the applicable salary and inclusive employment dates; and obtain correct filtered compensation metrics. Updates and audit entries succeed or fail together. Invalid, unauthorized, or conflicting writes leave stored data unchanged. Inactive employees retain their records and history and are excluded from reports. Core workflows work with a keyboard and expose the required feedback states.

The initial release serves one organization with one HR permission level and manages annual base salaries and effective-dated monthly base-pay estimates. Detailed rules and verification scenarios are defined in [Requirements Rules and Decisions](requirements-rules.md).
