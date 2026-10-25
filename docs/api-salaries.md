# Salary Changes and History API Contract

**Version:** 1.1. The two endpoints below follow [shared API conventions](api-conventions.md), [authentication](api-auth.md), and [employee data rules](api-employees.md).

| Method | Path | Success |
|---|---|---|
| POST | `/api/employees/{employeeId}/salary-changes` | 201 with the committed change |
| GET | `/api/employees/{employeeId}/salary-changes` | 200 with paginated history |

Both require enabled HR access and a valid public employee ID. Inactive employee history remains readable. Initial employee creation is not a salary change.

## 1. Shared request and response rules

GET accepts no body. POST requires JSON, CSRF and a matching session cookie. Return `Content-Type: application/json`, `Cache-Control: no-store`, and a fresh `X-Request-ID`. Reject unknown/null/duplicate fields and unsupported query parameters. Money uses the employee contract's positive decimal-string range and configured currency precision. The POST body limit is 16,384 bytes.

### Immutable change representation

```json
{
  "changeId": "chg-2ed38a7d-ae85-426a-b88c-4ba19bfded83",
  "employeeId": "EMP-004218",
  "previousAmount": "1320000.00",
  "newAmount": "1452000.00",
  "currency": "INR",
  "reason": "Annual compensation review",
  "actor": {
    "id": "hr-0001",
    "displayName": "Maya Rao"
  },
  "changedAt": "2026-10-25T05:30:00Z",
  "resultingEmployeeVersion": 4,
  "effectiveDate": "2026-10-15"
}
```

All properties are required/non-null. Change/user IDs are opaque strings; the employee ID follows its existing format. Amounts are decimal strings; `effectiveDate` is the HR-supplied calendar date on which the salary takes effect; `changedAt` is the server-generated UTC recording instant, and the resulting version is a nonnegative integer. `actor.displayName` is captured at change time so history/replay remains stable after a user rename. The actor's stable user ID remains linked to the HR account; disabled accounts do not erase history.

This representation is the salary/history operation's outcome, not a current employee snapshot. Later changes can make the latest employee version and salary different.

### Effective date and monthly application

Require `effectiveDate` on every new salary change. It must be a real ISO `YYYY-MM-DD` date, on or before the UTC business date, strictly after the latest recorded salary effective date, and within the employee's inclusive employment dates. Return 400 `VALIDATION_ERROR` with field code `INVALID_DATE`, `FUTURE_DATE_NOT_ALLOWED`, `EFFECTIVE_DATE_NOT_AFTER_LATEST`, or `INVALID_DATE_ORDER` as appropriate. Multiple changes in the same month are allowed when their effective dates increase.

A raise recorded with an effective date during a month applies to that **whole month's base-pay calculation**. Select the latest effective date on or before the selected month's end; only joining/leaving dates prorate the monthly amount. Two changes within one month use the later effective date for the whole month. Preserve earlier amounts/dates in history. `changedAt` controls audit order, not salary selection. Saving a backdated change can revise estimates for its month and following months; calculate fresh estimates from a consistent snapshot.

The initial salary baseline applies from its effective month, clipped to the joining date. The [monthly estimate contract](api-base-pay.md) defines selection, coverage, and calculations.

### Curl prerequisites

Complete [sign-in and CSRF refresh](api-auth.md#3-shared-curl-setup). Reuse its `BASE_URL`, `COOKIE_JAR`, and current `CSRF_TOKEN`. The examples assume a fictional active employee `EMP-004218` at salary INR 1,320,000.00 effective 2026-04-01 and version 3. They are independent of the employee contract's edit/inactive walkthrough. Fetch current details before a real edit.

## 2. POST /api/employees/{employeeId}/salary-changes

**Path:** Required employee ID. **Query parameters:** None.

| Header | Rule |
|---|---|
| `Accept: application/json` | Recommended |
| `Content-Type: application/json` | Required |
| `Cookie: JSESSIONID=<session>` | Required HR session |
| `X-CSRF-TOKEN: <token>` | Required token for that session |
| `Idempotency-Key: <UUID>` | Required canonical UUID, generated once for this operation |

| Body property | Type | Required validation |
|---|---|---|
| `annualBaseSalary` | Decimal string | Positive; employee salary range and currency precision; different from current amount |
| `effectiveDate` | Date string | Required; ISO calendar date satisfying the effective-date rules above |
| `reason` | String | Trimmed, nonblank, 1–1,000 Unicode code points |
| `version` | Integer | Nonnegative safe integer from the latest reviewed employee read |

Currency is taken from the employee; actor and time come from the server. Reject client-supplied currency, previous salary, actor, timestamp, or change ID. An unchanged amount produces 400 `VALIDATION_ERROR` with field code `SALARY_UNCHANGED`; do not add a history row or increment version.

Sample request:

```http
POST /api/employees/EMP-004218/salary-changes HTTP/1.1
Host: localhost:8080
Accept: application/json
Content-Type: application/json
Cookie: JSESSIONID=example-authenticated-session
X-CSRF-TOKEN: example-refreshed-csrf-token
Idempotency-Key: ad395a15-d088-4fb8-9da4-d3b5ce26668a
```

```json
{
  "annualBaseSalary": "1452000.00",
  "reason": "Annual compensation review",
  "version": 3,
  "effectiveDate": "2026-10-15"
}
```

First success returns the complete immutable change representation above, with:

```http
HTTP/1.1 201 Created
Content-Type: application/json
Cache-Control: no-store
X-Request-ID: 6bfcfa4c-8e36-4f72-8d08-6b453fa1ad53
Idempotency-Replayed: false
```

`Idempotency-Replayed` is a project-defined response header with literal value `true` or `false`. No single-change GET route or `Location` header is defined; the created change is identified by its body and appears in the employee history collection.

### Atomicity and replay

1. Authenticate, authorize and validate CSRF; validate path/header/body structure.
2. Look up an already committed key scoped to the stable authenticated actor ID, across that actor's salary operations.
3. If the employee path and canonical payload match, return the original 201 status and unchanged change body, with `Idempotency-Replayed: true` and a fresh tracing ID. Do this before current version, active-status, or unchanged-amount checks.
4. If a committed key is bound to a different employee/payload, return 409 `IDEMPOTENCY_KEY_REUSED` without exposing the original payload.
5. For a new key, load the employee; check version, active status, employment dates, effective-date chronology, amount, precision and reason. The current salary/effective date, history entry, and persisted key metadata commit in one transaction. Increment the shared employee version exactly once.

Canonical payload comparison includes employee ID, numeric salary value, effective date, trimmed reason and expected version. JSON property order/spacing and accepted equivalent decimal formatting do not distinguish operations. Do not normalize reason case or collapse internal whitespace. A new payload/version requires a new client key. Invalid/rejected operations do not reserve a success key; clients must still generate a new key when changing intent.

Persist a unique actor/key pair, payload fingerprint, original change fields and resulting version for as long as the history is retained. The initial release does not purge history or expire committed replay keys. Another actor's identical UUID is a separate operation and still faces current version checks.

For concurrent duplicate requests, resolve a losing uniqueness/version race after rollback by reading the committed operation. Return its original outcome if it matches. Never leave two salary changes/history entries. If the outcome is still unresolved within the configured database/request wait limit, return 503 `SERVICE_UNAVAILABLE` with `Retry-After` and instruct the caller to retain the same key. Do not call an unconfirmed write failed or successful.

A replay may succeed after the employee becomes inactive or receives another salary update: it only confirms a previous operation. It still requires current HR access and valid CSRF. Refetch employee details after success/replay to display the current state. Never silently replay writes after reauthentication without user review.

**Curl:**

```sh
# Set once for this operation; keep this value and payload for an uncertain retry.
SALARY_OPERATION_KEY='ad395a15-d088-4fb8-9da4-d3b5ce26668a'
curl --silent --show-error --fail-with-body --include \
  --request POST \
  --cookie "$COOKIE_JAR" --cookie-jar "$COOKIE_JAR" \
  --header 'Accept: application/json' \
  --header 'Content-Type: application/json' \
  --header "X-CSRF-TOKEN: $CSRF_TOKEN" \
  --header "Idempotency-Key: $SALARY_OPERATION_KEY" \
  --data-binary @- \
  "$BASE_URL/api/employees/EMP-004218/salary-changes" <<'JSON'
{
  "annualBaseSalary": "1452000.00",
  "reason": "Annual compensation review",
  "version": 3,
  "effectiveDate": "2026-10-15"
}
JSON
```

### Failure examples

Stale version (`409`):

```json
{
  "code": "VERSION_CONFLICT",
  "message": "This employee changed. Review the latest record before saving again.",
  "requestId": "a3a94bf9-f32c-49f3-87fe-287e35231480",
  "fieldErrors": [],
  "details": {
    "expectedVersion": 3,
    "currentVersion": 4
  }
}
```

Inactive employee (`409`):

```json
{
  "code": "EMPLOYEE_INACTIVE",
  "message": "Salary cannot be changed for an inactive employee.",
  "requestId": "7041d55a-4129-4717-9b58-d2bde510db7c",
  "fieldErrors": []
}
```

Incompatible committed key (`409`):

```json
{
  "code": "IDEMPOTENCY_KEY_REUSED",
  "message": "This operation key was already used for a different salary change.",
  "requestId": "16651632-b9c0-4d14-b6b1-031cfca271d9",
  "fieldErrors": []
}
```

Unchanged amount (`400`):

```json
{
  "code": "VALIDATION_ERROR",
  "message": "Enter a salary different from the current amount.",
  "requestId": "323462fb-3e0b-4bb9-83c8-1c08bdcfd876",
  "fieldErrors": [
    {
      "field": "annualBaseSalary",
      "code": "SALARY_UNCHANGED",
      "message": "The proposed salary matches the current salary."
    }
  ]
}
```

**Applicable failures:** Shared 400, 401, 403, 404, 405, 406, 409 as above, 413, 415, 500, 503. A missing/malformed idempotency UUID is 400 with a header field error. Validation/conflict errors require correction/review, not automatic retry. For an uncertain network/500/503 outcome, retry only the identical operation with its existing key, with bounded attempts and any returned delay.

## 3. GET /api/employees/{employeeId}/salary-changes

**Headers:** HR session cookie and `Accept: application/json`. No CSRF or body. **Path:** Required employee ID.

| Query parameter | Default | Rule |
|---|---|---|
| `page` | 0 | Integer 0–1,000,000 |
| `size` | 25 | Integer 1–100 |

No client-supplied sort, date filters, currency filters or other query parameters are accepted. Order by `changedAt` descending, then immutable internal change order descending as a tie-breaker. Return all currencies recorded for the employee; currency is part of each entry. The initial release fixes the employee currency after creation. Match the directory's page/count snapshot and out-of-range semantics; concurrent new changes may move offset pages between requests.

```http
GET /api/employees/EMP-004218/salary-changes?page=0&size=25 HTTP/1.1
Host: localhost:8080
Accept: application/json
Cookie: JSESSIONID=example-authenticated-session
```

Example success after the standalone salary-change example, assuming that fixture had no previous changes (`200`, shared JSON/cache/tracing headers):

```json
{
  "employeeId": "EMP-004218",
  "items": [
    {
      "changeId": "chg-2ed38a7d-ae85-426a-b88c-4ba19bfded83",
      "employeeId": "EMP-004218",
      "previousAmount": "1320000.00",
      "newAmount": "1452000.00",
      "currency": "INR",
      "reason": "Annual compensation review",
      "actor": {
        "id": "hr-0001",
        "displayName": "Maya Rao"
      },
      "changedAt": "2026-10-25T05:30:00Z",
      "resultingEmployeeVersion": 4,
      "effectiveDate": "2026-10-15"
    }
  ],
  "page": 0,
  "size": 25,
  "numberOfElements": 1,
  "totalElements": 1,
  "totalPages": 1,
  "sort": {
    "field": "changedAt",
    "direction": "desc"
  },
  "hasPrevious": false,
  "hasNext": false,
  "initialSalary": {
    "annualBaseSalary": "1320000.00",
    "currency": "INR",
    "effectiveDate": "2026-04-01",
    "recordedAt": "2026-04-01T03:45:00Z",
    "actor": {
      "id": "hr-0001",
      "displayName": "Maya Rao"
    }
  }
}
```

A real employee with no salary changes returns:

```json
{
  "employeeId": "EMP-010001",
  "items": [],
  "page": 0,
  "size": 25,
  "numberOfElements": 0,
  "totalElements": 0,
  "totalPages": 0,
  "sort": {
    "field": "changedAt",
    "direction": "desc"
  },
  "hasPrevious": false,
  "hasNext": false,
  "initialSalary": {
    "annualBaseSalary": "960000.00",
    "currency": "INR",
    "effectiveDate": "2026-10-25",
    "recordedAt": "2026-10-25T06:00:00Z",
    "actor": {
      "id": "hr-0001",
      "displayName": "Maya Rao"
    }
  }
}
```

`initialSalary` is required on every history page and contains the immutable baseline amount, currency, effective date, creation recording instant (`recordedAt`), and creation actor snapshot (`actor`, with the same shape as a change actor). `recordedAt` equals employee `createdAt`; it can differ from the effective date. It is separate from paginated changes and is displayed above the change list. Each change includes `effectiveDate` and `changedAt`. An empty change list still includes the initial salary. All fields are required. Page metadata has the same definitions as the directory contract. A nonexistent employee returns 404, not an empty history. Inactive employees return their preserved history. Replayed salary requests do not create extra entries.

**Curl:**

```sh
curl --silent --show-error --fail-with-body --get \
  --cookie "$COOKIE_JAR" --cookie-jar "$COOKIE_JAR" \
  --header 'Accept: application/json' \
  --data-urlencode 'page=0' \
  --data-urlencode 'size=25' \
  "$BASE_URL/api/employees/EMP-004218/salary-changes"
```

**Applicable failures:** Shared 400, 401, 403, 404, 405, 406, 415, 500, 503. GET is safe to retry and can observe newer history.

## 4. Required verification

Use [TDD and Oracle integration checks](testing-strategy.md) for amount/reason/date validation, whole-month raises, same-month multiple changes, baseline coverage, effective-date replay mismatches, unchanged salary, atomic history writes, all version races, stable actor snapshots, replay after later changes, mismatched keys, concurrent duplicate requests, access revocation, pagination and preservation of inactive history. Verify both curl commands against the running backend.
