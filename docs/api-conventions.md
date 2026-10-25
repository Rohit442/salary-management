# API Conventions and Endpoint Index

Shared representation, security, errors, and retry rules for the application APIs.

## Endpoint inventory

| Contract | Method | Path |
|---|---|---|
| [Authentication](api-auth.md) | GET | `/api/auth/csrf` |
| Authentication | POST | `/api/auth/login` |
| Authentication | GET | `/api/auth/me` |
| Authentication | POST | `/api/auth/logout` |
| [Employees](api-employees.md) | GET | `/api/employees` |
| Employees | GET | `/api/employees/{employeeId}` |
| Employees | POST | `/api/employees` |
| Employees | PUT | `/api/employees/{employeeId}/details` |
| Employees | PUT | `/api/employees/{employeeId}/status` |
| [Salary changes](api-salaries.md) | POST | `/api/employees/{employeeId}/salary-changes` |
| Salary changes | GET | `/api/employees/{employeeId}/salary-changes` |
| [Reports](api-reports.md) | GET | `/api/reports/compensation` |
| [Reference data](api-reference-data.md) | GET | `/api/reference-data` |
| [Monthly base pay](api-base-pay.md) | GET | `/api/employees/{employeeId}/base-pay-estimate` |

Feature exclusions are defined in the requirements’ [Out of scope](requirements.md#out-of-scope) section.

## Transport, representation and security

- Use the deployed HTTPS origin; local examples use `http://localhost:8080`.
- Session cookies, CSRF handling, access loss and sign-out follow [authentication](api-auth.md). Business endpoints require an enabled HR identity. POST/PUT require a valid CSRF token; GET has no body and does not require CSRF.
- Return JSON, `Cache-Control: no-store`, and a fresh server-generated `X-Request-ID` on every response, including retries and errors. Bodyless logout returns no JSON or Content-Type. Requests recommend `Accept: application/json`.
- JSON writes require `Content-Type: application/json`; login is the documented form-encoded exception. Reject unknown properties, invalid types, null required fields and duplicate property names. Never coerce numeric JSON into monetary strings.
- Monetary amounts are plain decimal strings, without exponent notation or grouping. Employee/salary writes use the employee contract's positive range and currency precision. Report totals are aggregates and may exceed one employee's input maximum; do not truncate or apply that input limit to aggregates.
- Timestamp strings are UTC ISO-8601 instants with `Z`. Employment and salary effective dates are ISO `YYYY-MM-DD` calendar dates; estimate months use `YYYY-MM`. UTC defines the business date. Identity strings are opaque except for the documented public employee ID pattern. Versions/counts/page numbers are nonnegative JSON safe integers.
- Protected responses never expose internal database keys, password data, session tokens, SQL errors, or stack traces. Keep credentials, salary bodies and change reasons out of routine logs.

## Common error envelope

```json
{
  "code": "VERSION_CONFLICT",
  "message": "This employee changed. Review the latest record before saving again.",
  "requestId": "84201bf0-2948-453e-b7e4-ff6151807d60",
  "fieldErrors": [],
  "details": {"expectedVersion": 3, "currentVersion": 4}
}
```

`code`, `message`, `requestId`, and `fieldErrors` are always required. Each field error has string `field`, `code`, and `message`; it identifies a body field, header, or query parameter without echoing its rejected value. `details` is optional and must be omitted when unused. Only the version-conflict example above currently defines detail properties. Both versions are nonnegative integers; `currentVersion` is a point-in-time diagnostic, not a replacement version to submit blindly. Clients branch on stable codes and ignore unrecognized optional response fields.

| Status | Stable codes | Rule |
|---|---|---|
| 400 | `VALIDATION_ERROR` | Malformed request, unknown fields/parameters, unsupported reference/grouping, invalid salary/reason/version; includes unchanged new salary |
| 401 | `INVALID_CREDENTIALS`, `AUTHENTICATION_REQUIRED` | Incorrect login, or missing/expired/revoked session as specified by endpoint |
| 403 | `ACCESS_DENIED`, `CSRF_INVALID` | HR access absent or write security token invalid |
| 404 | `EMPLOYEE_NOT_FOUND` | Authorized lookup of a validly formatted missing employee ID |
| 409 | `EMPLOYEE_ID_EXISTS`, `WORK_EMAIL_EXISTS`, `VERSION_CONFLICT`, `EMPLOYEE_INACTIVE`, `IDEMPOTENCY_KEY_REUSED`, `SALARY_HISTORY_INCOMPLETE` | Identity collision, stale version, invalid employee state, incompatible key reuse, or incomplete salary coverage for a monthly estimate |
| 405 | `METHOD_NOT_ALLOWED` | Include `Allow` |
| 406 | `NOT_ACCEPTABLE` | Client excludes JSON |
| 413 | `PAYLOAD_TOO_LARGE` | Endpoint request-body limit exceeded |
| 415 | `UNSUPPORTED_MEDIA_TYPE` | Wrong request encoding or body on bodyless endpoint |
| 429 | `TOO_MANY_ATTEMPTS` | Login throttling; include integer-seconds `Retry-After` |
| 500 | `INTERNAL_ERROR` | Unexpected failure; outcome of a write may need reconciliation |
| 503 | `SERVICE_UNAVAILABLE` | Temporary failure or unresolved in-flight salary operation; include `Retry-After` when recommending a delay |

Validation field codes include `REQUIRED`, `INVALID_FORMAT`, `INVALID_TYPE`, `UNKNOWN_FIELD`, `INVALID_PARAMETER`, `UNSUPPORTED_VALUE`, `TOO_LONG`, `NOT_UNIQUE`, `POSITIVE_AMOUNT_REQUIRED`, `INVALID_PRECISION`, `AMOUNT_OUT_OF_RANGE`, `SALARY_UNCHANGED`, `INVALID_DATE`, `INVALID_DATE_ORDER`, `FUTURE_DATE_NOT_ALLOWED`, and `EFFECTIVE_DATE_NOT_AFTER_LATEST`. Endpoint documents identify applicable statuses. Authentication/CSRF rejection can precede business validation.

## Versions, retries and identifiers

| Operation | Retry contract |
|---|---|
| GET | Safe to retry; each request can observe newer data |
| Employee POST | No automatic deduplication contract; reconcile by the same unique employee ID |
| Details/status PUT | Same intended state, with mandatory version checks; an old-version retry can return 409 |
| Salary-change POST | Reuse `Idempotency-Key` and equivalent payload; return the committed original outcome without another change |
| Login/logout | Follow session and token-refresh rules; do not infer success from a network failure |

`X-Request-ID`/error `requestId` identify one HTTP attempt for diagnostics. They are server generated. `Idempotency-Key` is a client-generated UUID identifying one salary-change operation and remains unchanged across its retries. It is a project-defined header contract; it is not an authentication credential. Do not use tracing IDs as deduplication keys.

All employee mutations share `version`, including detail edits, status and salary changes. Salary responses use `resultingEmployeeVersion` for the version produced by that recorded change; a replay does not claim that version is still current. Retrieve employee details for current state. No operation automatically upgrades a stale submitted version.

Do not automatically retry 400/403/404/409 errors. A 401 requires reauthentication and user review before replaying an interrupted write. Network/500/503 salary outcomes may be reconciled with the same operation key after current access and CSRF are valid. Bound retry attempts and respect `Retry-After`; surface unresolved failures instead of retrying indefinitely.

## Contract verification

Verify request/response schemas and curl examples against the running backend. Follow the [testing strategy](testing-strategy.md) for HTTP/security, Oracle transaction/concurrency, and essential browser checks.
