# Project working rules

These instructions reflect the user's explicit request for precise communication and ownership throughout this project.

- Define feature exclusions in `docs/requirements.md` under “Out of scope”; link to that section when needed instead of repeating exclusion disclaimers. Keep project-wide implementation status in the README. Retain concrete validation, security, and data-integrity rules in the relevant specifications.

- Keep communication and documentation concise and specific.
- Verify that a file exists before linking it or describing it as an available deliverable.
- Clearly distinguish decisions, plans, implementation, and verified results. A design document is not evidence of implementation.
- Report completion only after checking the saved result. State material verification gaps plainly.
- Record actual engineering decisions and their reasons in `docs/engineering-notes.md`; never invent decisions, test results, measurements, commits, or deployment status.
- When correcting a misleading statement, acknowledge it directly and fix affected documentation rather than adding another promise.
- Follow the current requirements and user decisions. Do not restore superseded technology choices or expand excluded product scope.
- Apply red–green–refactor to core business behavior and bug fixes: observe the expected test failure before implementing, make it pass, then refactor with tests green. Do not describe tests written afterward as TDD.
- Test observable behavior at the lowest useful level. Verify database transactions, constraints, aggregates, and concurrency against H2 with Liquibase migrations rather than mocks. Keep test databases isolated from the persistent local database.
- Keep units focused and dependencies explicit. Use constructor injection, avoid speculative abstractions, and do not introduce distributed infrastructure without a demonstrated requirement.
- Keep project artifacts focused on adopted engineering decisions, rationale, sources, and actual verification. Omit the conversational research process; retain relevant implementation-related AI-use evidence required by the assessment.

## Backend coding patterns

- Keep schema changes and seed writes under Liquibase. Define static HR-user and currency seed records as native YAML changes, not Java seeders or startup hooks.

- Organize by feature and separate controllers, request validation, service implementation, and data access. Keep call paths short; avoid pass-through layers and speculative abstractions.
- Keep request validations in dedicated Java validator files. Controller methods declare an error list, validate applicable headers/body/parameters inside a try block, call the service only when validation succeeds, and return the contract's status and body. Catch and translate exceptions appropriately; use shared exception handling to format error responses.
- Use SLF4J. Log an exception once at its handling boundary with a request ID and appropriate severity; exclude credentials, session tokens, salary payloads, and change reasons.
- Declare service operations in feature interfaces. Default implementations throw a custom `UnimplementedMethodException`; service implementation classes provide the actual behavior. Implement all exposed routes before describing them as functional.
- Centralize SQL execution and connection/resource handling in a generic SQL utility. Never manually open connections in controllers, validators, or services. Preserve one Spring-managed transaction for salary, history, version, and replay metadata; do not open and commit an independent connection for each statement.
- Use typed DTOs, generics, collections, enhanced loops, comparators, and try-with-resources where appropriate. Prefer readable object-oriented code and constructor injection.
- If outbound HTTP calls become necessary, centralize their execution in HTTP utilities with method-specific operations. Add these utilities when a real integration needs them.
- Apply the same separation of concerns and focused validation patterns to the frontend when frontend work begins; current implementation scope is backend only.
