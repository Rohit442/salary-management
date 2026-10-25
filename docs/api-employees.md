# Employee Management API Contract

**Version:** 1.1.

This contract defines employee directory, creation, details, and status operations. Follow [authentication](api-auth.md) and [shared API conventions](api-conventions.md).

## 1. Endpoint summary

| Method | Path | Success | Purpose |
|---|---|---|---|
| GET | `/api/employees` | 200 | Search/filter/sort a paginated directory |
| GET | `/api/employees/{employeeId}` | 200 | Read a complete employee record |
| POST | `/api/employees` | 201 | Create an active employee with initial salary |
| PUT | `/api/employees/{employeeId}/details` | 200 | Replace all editable employee details |
| PUT | `/api/employees/{employeeId}/status` | 200 | Set employment status to inactive |

`{employeeId}` is the public, immutable employee ID, such as `EMP-004218`, not the internal H2 primary key. PUT replaces the entire named subresource; it does not replace the complete employee, salary, or history.

## 2. Shared protocol and access

- Local example origin: `http://localhost:8080`; deployed origin requires HTTPS. 
- All five endpoints require an authenticated, enabled HR user. Enforce this in the backend and honor account/permission revocation.
- Every write requires the current `X-CSRF-TOKEN` and its matching session cookie. Follow the sign-in/token-refresh flow in [api-auth.md](api-auth.md).
- GET requests have no body. POST and PUT require UTF-8 JSON objects and `Content-Type: application/json`; reject malformed JSON, duplicate property names, unknown fields, and invalid types. Do not coerce JSON numbers into money strings or strings into version integers.
- Return JSON for success/errors, `Cache-Control: no-store`, and a server-generated `X-Request-ID`. No HTML redirects. Never log salary bodies or sensitive employee data.
- Responses contain only application DTOs. Internal primary keys, password data, Hibernate fields, and session identifiers are excluded. Unknown future response fields may be ignored by clients.
- Write-body limit: 16,384 bytes. Return 413 for larger payloads. No query parameters are accepted by detail/create/PUT endpoints. Reject unknown directory parameters with 400.

| Header | Request or response | Requirement |
|---|---|---|
| `Accept: application/json` | Request | Recommended for every endpoint |
| `Cookie: JSESSIONID=<opaque>` | Request | Required authenticated session; handled automatically by browser/curl jar |
| `Content-Type: application/json` | POST/PUT request | Required; omit for bodyless GET |
| `X-CSRF-TOKEN: <token>` | POST/PUT request | Required; not needed for GET |
| `Content-Type: application/json` | Response | Present on all JSON success/errors |
| `Cache-Control: no-store` | Response | Required |
| `X-Request-ID: <UUID>` | Response | Matches error JSON correlation ID |
| `Location: /api/employees/<employeeId>` | Create response | Required on 201 |
| `Allow` | 405 response | Lists supported methods for the matched path |

Concurrency uses a JSON `version` field, not ETag/If-Match. No idempotency-key behavior is defined for employee creation or these PUT operations. The [salary contract](api-salaries.md) defines its separate client-generated `Idempotency-Key`; `X-Request-ID` remains a server-generated tracing ID, not a deduplication key.

## 3. Data definitions and validation

All required strings are trimmed and must remain nonblank, unless stated otherwise. Lengths below are maximum Unicode code-point counts after trimming; migrations must enforce compatible H2 character semantics. Persist the normalized work email in lowercase. Trim employee IDs, then require their uppercase pattern; do not silently change an existing ID.

| Field | JSON type | Contract rule |
|---|---|---|
| `employeeId` | String | `EMP-` followed by exactly six digits; unique across active/inactive records; immutable |
| `fullName` | String | 1–200 characters; preserve spelling and case |
| `workEmail` | String | Valid work-email syntax; maximum 254 characters; unique after trimming/case normalization |
| `countryCode` | String | Supported uppercase ISO-style two-letter country code |
| `department` | String | Supported canonical department value; maximum 100 characters |
| `jobTitle` | String | 1–200 characters |
| `jobLevel` | String | Supported canonical level; maximum 20 characters |
| `joiningDate` | Date string | Required ISO `YYYY-MM-DD`, valid Gregorian date from `0001-01-01` to `9999-12-31`; on or before the UTC business date |
| `lastEmploymentDate` | Date string or null | Inclusive final employment date; null means open-ended; on or after joining date; future dates permitted |
| `initialSalaryEffectiveFrom` | Date string | Required at creation; immutable thereafter; between joining date and the UTC business date |
| `salaryEffectiveFrom` | Date string | Read-only effective date of the latest recorded salary; initially equals `initialSalaryEffectiveFrom` |
| `employmentStatus` | String | Response: `ACTIVE` or `INACTIVE`; status write accepts only `INACTIVE` |
| `annualBaseSalary` | Decimal string | Positive plain decimal; no signs, grouping separators, whitespace, or exponent notation inside the string; maximum 15 integer digits and currency-permitted fractional digits |
| `currency` | String | Supported uppercase three-letter currency code; fixed after creation |
| `version` | Integer | Nonnegative safe JSON integer; returned on reads/writes and required for PUT |
| `createdAt`, `updatedAt` | String | Server-generated ISO-8601 instant, serialized in UTC with `Z` |

For an initial two-fraction-digit currency, valid input includes `"1320000"`, `"1320000.0"`, or `"1320000.00"`; normalize the response to `"1320000.00"`. Reject `"1,320,000.00"`, `"1e6"`, numeric JSON `1320000`, zero, negative values, and more than the allowed fractional digits. Reject excess precision rather than rounding an input, even when additional digits are zeros. Money is stored with H2 `DECIMAL(19,4)` and Java `BigDecimal`; the API enforces the tighter currency precision and at most 15 integer digits.

Initial reference vocabulary for this contract:

| Category | Accepted initial values |
|---|---|
| Countries | `IN`, `US`, `GB`, `DE`, `SG` |
| Currencies | `INR`, `USD`, `GBP`, `EUR`, `SGD`; each permits 2 fractional digits |
| Departments | `Engineering`, `Product`, `Design`, `Finance`, `Operations`, `HR` |
| Job levels | `L1`, `L2`, `L3`, `L4`, `L5`, `L6` |

The [reference-data endpoint](api-reference-data.md) supplies this vocabulary and precision from the same definitions used by backend validation and seeding; additions require contract/fixture updates. Country and currency are validated independently: do not infer currency from country. Changing country through details does not change salary currency.

### Employee response schema

Every item in a directory page and every detail/create/PUT success uses this complete shape. All fields shown are required. `lastEmploymentDate` is nullable; other fields are non-null:

```json
{
  "employeeId": "EMP-004218",
  "fullName": "Priya Sharma",
  "workEmail": "priya.sharma@acme.example",
  "countryCode": "IN",
  "department": "Engineering",
  "jobTitle": "Senior Software Engineer",
  "jobLevel": "L4",
  "employmentStatus": "ACTIVE",
  "annualBaseSalary": "1320000.00",
  "currency": "INR",
  "version": 3,
  "createdAt": "2026-04-01T03:45:00Z",
  "updatedAt": "2026-10-25T05:00:00Z",
  "joiningDate": "2026-04-01",
  "lastEmploymentDate": null,
  "initialSalaryEffectiveFrom": "2026-04-01",
  "salaryEffectiveFrom": "2026-04-01"
}
```

Employment dates are calendar dates without timezones. Use UTC to determine the business date for validation. Inactivation controls access to salary writes and inclusion in annual reports; it does not set an employment end date. Saving a future last employment date does not inactivate the record. Monthly estimates use employment dates independently of active/inactive status.

Salary always means annual base salary. Use `currency` when formatting it; never assume a symbol or convert currencies in these APIs. Reads do not embed salary-change history. The returned `version` governs detail, status and salary updates alike. Report aggregates may exceed one employee's salary cap; do not apply this 15-integer-digit input limit to aggregate outputs.

### Shared errors

Use the [shared error envelope](api-conventions.md#common-error-envelope): four required fields and optional documented `details`. Employee version conflicts include the two integer versions shown below. Do not return exception text or rejected values. Salary-specific conflict codes do not change these employee endpoints' applicable errors.

```json
{
  "code": "VALIDATION_ERROR",
  "message": "Check the employee details.",
  "requestId": "a1fd2ff2-a80a-4129-8c9e-4c4b37d6ae26",
  "fieldErrors": [
    {
      "field": "annualBaseSalary",
      "code": "POSITIVE_AMOUNT_REQUIRED",
      "message": "Annual base salary must be greater than zero."
    }
  ]
}
```

| Status | Codes | Meaning |
|---|---|---|
| 400 | `VALIDATION_ERROR` | Malformed ID/body/query, omitted required field, null/unknown field, unsupported reference value, invalid salary or version |
| 401 | `AUTHENTICATION_REQUIRED` | Missing/expired/revoked authentication |
| 403 | `ACCESS_DENIED`, `CSRF_INVALID` | HR access absent or write CSRF invalid |
| 404 | `EMPLOYEE_NOT_FOUND` | Validly formatted employee ID does not exist |
| 409 | `EMPLOYEE_ID_EXISTS`, `WORK_EMAIL_EXISTS`, `VERSION_CONFLICT` | Unique-identity collision or stale version |
| 405 / 406 | `METHOD_NOT_ALLOWED`, `NOT_ACCEPTABLE` | Wrong method or unacceptable response format |
| 413 / 415 | `PAYLOAD_TOO_LARGE`, `UNSUPPORTED_MEDIA_TYPE` | Excessive body or unsupported request media type/body on GET |
| 500 / 503 | `INTERNAL_ERROR`, `SERVICE_UNAVAILABLE` | Unexpected failure or temporary unavailability |

Validation failure examples include `REQUIRED`, `INVALID_FORMAT`, `UNSUPPORTED_VALUE`, `UNKNOWN_FIELD`, `INVALID_TYPE`, `TOO_LONG`, `INVALID_PARAMETER`, and salary-specific precision/range codes. `field` identifies a JSON property or query parameter. For a non-field error, use `fieldErrors: []`.

Authenticate/authorize before reading employee data. Security filters may return 401/403 before body validation. For authorized requests, validate the request shape, then load the employee and apply version/uniqueness checks. No failed mutation may partially modify stored data.

## 4. Curl prerequisites

Complete the [authentication curl flow](api-auth.md#3-shared-curl-setup), including refreshing the CSRF token after successful sign-in. Reuse `BASE_URL`, `COOKIE_JAR`, and `CSRF_TOKEN`:

```sh
BASE_URL='http://localhost:8080'
COOKIE_JAR='./acme-auth.cookies'
```

The file must already contain an authenticated HR session; the setup above alone does not sign in. `CSRF_TOKEN` must be the refreshed token for that session. Curl examples below are templates for the future server. The pre-existing-record examples use the fixture `EMP-004218`; creation uses `EMP-010001` and is independent of those edit examples.

## 5. GET /api/employees

**Purpose:** List employees, including active and inactive records. **Body/path parameters:** None. **Headers:** Authenticated cookie and `Accept`.

| Query parameter | Type/default | Rules |
|---|---|---|
| `q` | Optional string | Trimmed; 1–100 characters when supplied; case-insensitive literal substring match on full name or employee ID |
| `country` | Repeated optional string | Supported country codes; OR within category |
| `department` | Repeated optional string | Supported canonical department values; OR within category |
| `jobLevel` | Repeated optional string | Supported levels; OR within category |
| `currency` | Repeated optional string | Supported currency codes; OR within category |
| `page` | Integer, default `0` | Zero-based, minimum 0, maximum 1,000,000 |
| `size` | Integer, default `25` | 1–100 |
| `sort` | String, default `fullName,asc` | One `<field>,<direction>`; fields: `fullName`, `employeeId`, `countryCode`, `department`, `jobLevel`, `employmentStatus`; directions: `asc`, `desc` |

Apply `q` and different filter categories with AND semantics. Deduplicate repeated filter values; reject empty values, unsupported values, comma-packed filter lists, and repeated scalar parameters. Allow at most 20 supplied values per filter category before deduplication. Match search text literally: `%` and `_` are not wildcard operators. Escape SQL pattern characters and bind values. Reference filters use canonical case. Search is case-insensitive, not accent-insensitive.

Sort case-insensitively for text and use internal employee ID ascending as the deterministic tie-breaker. Salary sorting across currencies is not supported. These are offset pages, not a snapshot spanning multiple requests; concurrent changes can move rows. Return page content and its count from one consistent snapshot. A page past the last result returns an empty page with the actual total, not 404.

Sample request:

```http
GET /api/employees?q=Priya&country=IN&department=Engineering&jobLevel=L4&currency=INR&page=0&size=25&sort=fullName,asc HTTP/1.1
Host: localhost:8080
Accept: application/json
Cookie: JSESSIONID=example-authenticated-session
```

Success (`200`, shared response headers):

```json
{
  "items": [
    {
      "employeeId": "EMP-004218",
      "fullName": "Priya Sharma",
      "workEmail": "priya.sharma@acme.example",
      "countryCode": "IN",
      "department": "Engineering",
      "jobTitle": "Senior Software Engineer",
      "jobLevel": "L4",
      "employmentStatus": "ACTIVE",
      "annualBaseSalary": "1320000.00",
      "currency": "INR",
      "version": 3,
      "createdAt": "2026-04-01T03:45:00Z",
      "updatedAt": "2026-10-25T05:00:00Z",
      "joiningDate": "2026-04-01",
      "lastEmploymentDate": null,
      "initialSalaryEffectiveFrom": "2026-04-01",
      "salaryEffectiveFrom": "2026-04-01"
    }
  ],
  "page": 0,
  "size": 25,
  "numberOfElements": 1,
  "totalElements": 1,
  "totalPages": 1,
  "sort": {
    "field": "fullName",
    "direction": "asc"
  },
  "hasPrevious": false,
  "hasNext": false
}
```

Pagination counts are nonnegative integers; `size` is the requested/default page size, not the number of returned rows. `totalPages` is `ceil(totalElements / size)`. `hasPrevious` means `page > 0`; `hasNext` means `page + 1 < totalPages`. Empty matches:

```json
{
  "items": [],
  "page": 0,
  "size": 25,
  "numberOfElements": 0,
  "totalElements": 0,
  "totalPages": 0,
  "sort": {
    "field": "fullName",
    "direction": "asc"
  },
  "hasPrevious": false,
  "hasNext": false
}
```

**Curl:**

```sh
curl --silent --show-error --fail-with-body --get \
  --cookie "$COOKIE_JAR" --cookie-jar "$COOKIE_JAR" \
  --header 'Accept: application/json' \
  --data-urlencode 'q=Priya' \
  --data-urlencode 'country=IN' \
  --data-urlencode 'department=Engineering' \
  --data-urlencode 'jobLevel=L4' \
  --data-urlencode 'currency=INR' \
  --data-urlencode 'page=0' \
  --data-urlencode 'size=25' \
  --data-urlencode 'sort=fullName,asc' \
  "$BASE_URL/api/employees"
```

For multiple countries, repeat `--data-urlencode 'country=US'` alongside `country=IN`; do not send `country=IN,US`. **Failures:** 400, 401, 403, 405, 406, 415, 500, 503 using the shared codes. A directory read is safe to retry; results may change between reads.

## 6. GET /api/employees/{employeeId}

**Purpose:** Read one complete employee, including inactive records. **Path:** Required `employeeId` following the shared format. **Query/body:** None. **Headers:** Authenticated cookie and `Accept`.

```http
GET /api/employees/EMP-004218 HTTP/1.1
Host: localhost:8080
Accept: application/json
Cookie: JSESSIONID=example-authenticated-session
```

Success: `200` with the complete employee JSON from section 3 and shared response headers. The returned version must be supplied when submitting details/status changes. Example success headers:

```http
HTTP/1.1 200 OK
Content-Type: application/json
Cache-Control: no-store
X-Request-ID: 6b6fa26c-62c2-4fa7-81ad-45cf4cf11fd1
```

**Curl:**

```sh
curl --silent --show-error --fail-with-body --include \
  --cookie "$COOKIE_JAR" --cookie-jar "$COOKIE_JAR" \
  --header 'Accept: application/json' \
  "$BASE_URL/api/employees/EMP-004218"
```

Example valid-ID lookup failure (`404`):

```json
{
  "code": "EMPLOYEE_NOT_FOUND",
  "message": "Employee was not found.",
  "requestId": "9f463899-f468-43fe-909a-769c5bb661eb",
  "fieldErrors": []
}
```

**Failures:** 400, 401, 403, 404, 405, 406, 415, 500, 503. An invalid ID format produces 400; a valid absent ID produces 404. Reads are safe to retry.

## 7. POST /api/employees

**Purpose:** Create an employee and their initial salary atomically. **Path/query:** None. **Headers:** Authenticated cookie, `Accept`, JSON `Content-Type`, and CSRF token.

All eleven body properties are required: `employeeId`, `fullName`, `workEmail`, `countryCode`, `department`, `jobTitle`, `jobLevel`, `annualBaseSalary`, `currency`, `joiningDate`, `initialSalaryEffectiveFrom`. Their types/limits are in section 3. Do not send `version`, `employmentStatus`, timestamps, actor, or change reason. The server sets active status, `lastEmploymentDate: null`, initial version 0, and `salaryEffectiveFrom` equal to `initialSalaryEffectiveFrom`.

```http
POST /api/employees HTTP/1.1
Host: localhost:8080
Accept: application/json
Content-Type: application/json
Cookie: JSESSIONID=example-authenticated-session
X-CSRF-TOKEN: example-refreshed-csrf-token
```

```json
{
  "employeeId": "EMP-010001",
  "fullName": "Ananya Iyer",
  "workEmail": "ananya.iyer@acme.example",
  "countryCode": "IN",
  "department": "Engineering",
  "jobTitle": "Software Engineer",
  "jobLevel": "L3",
  "annualBaseSalary": "960000.00",
  "currency": "INR",
  "joiningDate": "2026-10-25",
  "initialSalaryEffectiveFrom": "2026-10-25"
}
```

Example success headers and body:

```http
HTTP/1.1 201 Created
Location: /api/employees/EMP-010001
Content-Type: application/json
Cache-Control: no-store
X-Request-ID: 51bed696-c40b-4808-b752-2bd4b2d890ea
```

```json
{
  "employeeId": "EMP-010001",
  "fullName": "Ananya Iyer",
  "workEmail": "ananya.iyer@acme.example",
  "countryCode": "IN",
  "department": "Engineering",
  "jobTitle": "Software Engineer",
  "jobLevel": "L3",
  "employmentStatus": "ACTIVE",
  "annualBaseSalary": "960000.00",
  "currency": "INR",
  "version": 0,
  "createdAt": "2026-10-25T06:00:00Z",
  "updatedAt": "2026-10-25T06:00:00Z",
  "joiningDate": "2026-10-25",
  "lastEmploymentDate": null,
  "initialSalaryEffectiveFrom": "2026-10-25",
  "salaryEffectiveFrom": "2026-10-25"
}
```

Persist the initial amount, currency, effective date, authenticated creation actor ID/display-name snapshot, and creation timestamp as an immutable salary baseline in the employee-creation transaction. Initial salary does not create a previous-to-new salary history entry. No employee login account is created. Uniqueness constraints must prevent concurrent duplicate creation; do not rely only on a pre-insert lookup.

**Curl:**

```sh
curl --silent --show-error --fail-with-body --include \
  --request POST \
  --cookie "$COOKIE_JAR" --cookie-jar "$COOKIE_JAR" \
  --header 'Accept: application/json' \
  --header 'Content-Type: application/json' \
  --header "X-CSRF-TOKEN: $CSRF_TOKEN" \
  --data-binary @- \
  "$BASE_URL/api/employees" <<'JSON'
{
  "employeeId": "EMP-010001",
  "fullName": "Ananya Iyer",
  "workEmail": "ananya.iyer@acme.example",
  "countryCode": "IN",
  "department": "Engineering",
  "jobTitle": "Software Engineer",
  "jobLevel": "L3",
  "annualBaseSalary": "960000.00",
  "currency": "INR",
  "joiningDate": "2026-10-25",
  "initialSalaryEffectiveFrom": "2026-10-25"
}
JSON
```

Example duplicate-ID response (`409`):

```json
{
  "code": "EMPLOYEE_ID_EXISTS",
  "message": "This employee ID is already in use.",
  "requestId": "e5c9e15d-5d1e-48cd-a6dd-545551a08865",
  "fieldErrors": [
    {
      "field": "employeeId",
      "code": "NOT_UNIQUE",
      "message": "Employee ID must be unique."
    }
  ]
}
```

For a duplicate normalized email, return `WORK_EMAIL_EXISTS`, with a `workEmail` field error and the message “Work email must be unique.” If multiple constraints fail, the client must not depend on which conflict is reported first.

**Retry rule:** POST is not inherently idempotent. After a timeout, retrieve the same employee ID. If it exists, inspect its current data before deciding what happened; existence alone does not prove this request created it. If it is absent, retry the same creation with the same ID. A concurrent/late original commit can still yield 409. Never generate a replacement ID automatically or overwrite an existing employee on retry.

**Failures:** 400, 401, 403, 405, 406, 409 (identity collisions), 413, 415, 500, 503. No partial record may remain on a failed transaction.

## 8. PUT /api/employees/{employeeId}/details

**Purpose:** Replace the complete editable-details subresource. **Path:** Required employee ID. **Query:** None. **Headers:** Authenticated cookie, `Accept`, JSON `Content-Type`, and CSRF token.

All nine body properties are required: `fullName`, `workEmail`, `countryCode`, `department`, `jobTitle`, `jobLevel`, `joiningDate`, `lastEmploymentDate`, `version`. Only `lastEmploymentDate` may be null; send null explicitly to clear it. Omitted properties and nulls for other fields are validation errors. `version` is the version returned by the latest reviewed employee read. Do not send employee ID, salary, currency, status, or timestamps in this body; reject them as unknown fields.

```http
PUT /api/employees/EMP-004218/details HTTP/1.1
Host: localhost:8080
Accept: application/json
Content-Type: application/json
Cookie: JSESSIONID=example-authenticated-session
X-CSRF-TOKEN: example-refreshed-csrf-token
```

```json
{
  "fullName": "Priya Sharma",
  "workEmail": "priya.sharma@acme.example",
  "countryCode": "IN",
  "department": "Product",
  "jobTitle": "Technical Product Manager",
  "jobLevel": "L4",
  "version": 3,
  "joiningDate": "2026-04-01",
  "lastEmploymentDate": "2026-11-12"
}
```

This example updates the department/title and records a future last employment date. Status remains `ACTIVE` until HR explicitly marks the employee inactive.

Success (`200`, shared response headers):

```json
{
  "employeeId": "EMP-004218",
  "fullName": "Priya Sharma",
  "workEmail": "priya.sharma@acme.example",
  "countryCode": "IN",
  "department": "Product",
  "jobTitle": "Technical Product Manager",
  "jobLevel": "L4",
  "employmentStatus": "ACTIVE",
  "annualBaseSalary": "1320000.00",
  "currency": "INR",
  "version": 4,
  "createdAt": "2026-04-01T03:45:00Z",
  "updatedAt": "2026-10-25T06:10:00Z",
  "joiningDate": "2026-04-01",
  "lastEmploymentDate": "2026-11-12",
  "initialSalaryEffectiveFrom": "2026-04-01",
  "salaryEffectiveFrom": "2026-04-01"
}
```

Validate the complete proposed employment interval atomically: joining date must be on or before the immutable initial salary effective date; a non-null last employment date must be on or after joining date and every recorded salary effective date. Reject contradictory date edits with 400 `VALIDATION_ERROR` and field code `INVALID_DATE_ORDER`. Dates and salary changes participate in the same version check, including concurrent end-date/salary races. Changing employment dates recalculates estimates on the next read; it preserves the salary baseline and audit entries.

This operation is allowed for inactive employees too; it cannot reactivate them or change their salary. It does not add salary history. After normalization, if all submitted editable values already equal the stored values and the version matches, return 200 without changing version or `updatedAt`. Actual changes increment version exactly once and preserve `createdAt`.

**Curl:**

```sh
curl --silent --show-error --fail-with-body --include \
  --request PUT \
  --cookie "$COOKIE_JAR" --cookie-jar "$COOKIE_JAR" \
  --header 'Accept: application/json' \
  --header 'Content-Type: application/json' \
  --header "X-CSRF-TOKEN: $CSRF_TOKEN" \
  --data-binary @- \
  "$BASE_URL/api/employees/EMP-004218/details" <<'JSON'
{
  "fullName": "Priya Sharma",
  "workEmail": "priya.sharma@acme.example",
  "countryCode": "IN",
  "department": "Product",
  "jobTitle": "Technical Product Manager",
  "jobLevel": "L4",
  "version": 3,
  "joiningDate": "2026-04-01",
  "lastEmploymentDate": "2026-11-12"
}
JSON
```

Example stale-version response (`409`):

```json
{
  "code": "VERSION_CONFLICT",
  "message": "This employee changed while you were editing. Review the latest record before saving again.",
  "requestId": "c2667a13-2c1d-4bca-9635-c3a885a9402e",
  "fieldErrors": [],
  "details": {
    "expectedVersion": 3,
    "currentVersion": 4
  }
}
```

The reported `currentVersion` is diagnostic, not permission to resubmit blindly; another write can occur immediately afterward. Fetch the complete record and let the user reconcile the draft. Version validation precedes no-op detection, even when submitted values match current data.

**Retry rule:** Repeating the same PUT has no additional intended business effect, but identical responses are not guaranteed. If the first write committed and its response was lost, the old-version retry returns 409. Read the record to reconcile the outcome. Never silently replace the submitted version with the latest version, which could overwrite someone else's changes.

**Failures:** 400, 401, 403, 404, 405, 406, 409 (`WORK_EMAIL_EXISTS` or `VERSION_CONFLICT`), 413, 415, 500, 503.

## 9. PUT /api/employees/{employeeId}/status

**Purpose:** Set an employee's status to inactive after the UI confirmation. **Path:** Required employee ID. **Query:** None. **Headers:** Authenticated cookie, `Accept`, JSON `Content-Type`, and CSRF token.

Both fields are required: `employmentStatus` must be the exact string `INACTIVE`; `version` must be the version reviewed before confirmation. Sending `ACTIVE` returns 400 `VALIDATION_ERROR`/`UNSUPPORTED_VALUE`. Do not accept other employee properties in this body.

```http
PUT /api/employees/EMP-004218/status HTTP/1.1
Host: localhost:8080
Accept: application/json
Content-Type: application/json
Cookie: JSESSIONID=example-authenticated-session
X-CSRF-TOKEN: example-refreshed-csrf-token
```

```json
{
  "employmentStatus": "INACTIVE",
  "version": 4
}
```

Success (`200`, shared response headers), following the detail-edit example:

```json
{
  "employeeId": "EMP-004218",
  "fullName": "Priya Sharma",
  "workEmail": "priya.sharma@acme.example",
  "countryCode": "IN",
  "department": "Product",
  "jobTitle": "Technical Product Manager",
  "jobLevel": "L4",
  "employmentStatus": "INACTIVE",
  "annualBaseSalary": "1320000.00",
  "currency": "INR",
  "version": 5,
  "createdAt": "2026-04-01T03:45:00Z",
  "updatedAt": "2026-10-25T06:15:00Z",
  "joiningDate": "2026-04-01",
  "lastEmploymentDate": "2026-11-12",
  "initialSalaryEffectiveFrom": "2026-04-01",
  "salaryEffectiveFrom": "2026-04-01"
}
```

The current salary, employment dates, and existing history remain intact. Preserve access to monthly estimates, including for the leaving month. Future compensation reports exclude the employee, and future salary updates must reject inactive status. An already inactive employee with the current matching version returns 200 as a no-op, leaving version/time unchanged. A stale version still returns 409, even when the current status is already inactive.

**Curl:**

```sh
curl --silent --show-error --fail-with-body --include \
  --request PUT \
  --cookie "$COOKIE_JAR" --cookie-jar "$COOKIE_JAR" \
  --header 'Accept: application/json' \
  --header 'Content-Type: application/json' \
  --header "X-CSRF-TOKEN: $CSRF_TOKEN" \
  --data-binary @- \
  "$BASE_URL/api/employees/EMP-004218/status" <<'JSON'
{
  "employmentStatus": "INACTIVE",
  "version": 4
}
JSON
```

This example assumes the previous details update succeeded at version 4. In actual use read the current version; do not hardcode sample versions. **Failures:** 400, 401, 403, 404, 405, 406, 409 (`VERSION_CONFLICT` with the same shape as section 8), 413, 415, 500, 503.

**Retry rule:** Use the same body/version for an uncertain retry; if it conflicts, read the record. If it is already inactive, the desired state currently holds, but that does not establish which request made the change. If it is active with a newer version, require renewed review/confirmation before another write. Do not toggle status or automatically retry with a new version.

## 10. Transaction and verification requirements

Detail, status, and salary writes share the employee's optimistic version. A simultaneous salary/status race must have one winner and a rejected stale write, never a salary update applied after an inactive transition using an obsolete version. Flush/commit-time conflicts must map to 409 after transaction rollback. Success responses are sent only after commit.

Use [red–green–refactor](testing-strategy.md) to verify:

- All five request/response schemas, required headers, access checks, CSRF rejection, and error mapping.
- Directory defaults, multi-select AND/OR behavior, literal search escaping, deterministic ordering, empty/out-of-range pages, and consistent page/count snapshots.
- Employee ID/email uniqueness against H2, including concurrent creation and inactive records; no partial create and no invented initial salary-change entry.
- Complete replacement semantics for details, forbidden-field rejection, immutable identity/currency/baseline, employment date validation, inclusive termination, future end dates, inactive detail edits, and no-op behavior.
- Stale versions, real concurrent transactions, uncertain retry reconciliation, and status/salary race behavior against H2.
- Status preservation of salary/history, report exclusion, and salary-update rejection after inactivation when those features exist.
- Each of the five curl examples against the running backend, with real responses recorded separately from these examples.

## Related documents

- [Authentication contract](api-auth.md)
- [Requirements](requirements.md), [business rules](requirements-rules.md), and [technical design](technical-design.md)
- [Testing strategy](testing-strategy.md) and [engineering decisions](engineering-notes.md)
