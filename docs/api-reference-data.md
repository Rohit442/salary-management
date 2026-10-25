# Reference Data API Contract

**Version:** 1.0. This read-only endpoint supplies the backend-owned vocabulary used by employee forms, filters and salary formatting. Follow [shared API conventions](api-conventions.md).

## GET /api/reference-data

**Access:** Authenticated, enabled HR user. **Path/query/body:** None; reject unknown query parameters. **Request headers:** Session cookie and `Accept: application/json`. No CSRF token is required for this GET.

```http
GET /api/reference-data HTTP/1.1
Host: localhost:8080
Accept: application/json
Cookie: JSESSIONID=example-authenticated-session
```

Example success headers and complete initial response:

```http
HTTP/1.1 200 OK
Content-Type: application/json
Cache-Control: no-store
X-Request-ID: 01362e7b-c0da-4395-a723-419959442bb9
```

```json
{
  "countries": [
    {"code": "DE", "label": "Germany"},
    {"code": "GB", "label": "United Kingdom"},
    {"code": "IN", "label": "India"},
    {"code": "SG", "label": "Singapore"},
    {"code": "US", "label": "United States"}
  ],
  "departments": [
    {"value": "Design", "label": "Design"},
    {"value": "Engineering", "label": "Engineering"},
    {"value": "Finance", "label": "Finance"},
    {"value": "HR", "label": "Human Resources"},
    {"value": "Operations", "label": "Operations"},
    {"value": "Product", "label": "Product"}
  ],
  "jobLevels": [
    {"value": "L1", "label": "L1"},
    {"value": "L2", "label": "L2"},
    {"value": "L3", "label": "L3"},
    {"value": "L4", "label": "L4"},
    {"value": "L5", "label": "L5"},
    {"value": "L6", "label": "L6"}
  ],
  "currencies": [
    {"code": "EUR", "label": "Euro", "fractionDigits": 2},
    {"code": "GBP", "label": "Pound sterling", "fractionDigits": 2},
    {"code": "INR", "label": "Indian rupee", "fractionDigits": 2},
    {"code": "SGD", "label": "Singapore dollar", "fractionDigits": 2},
    {"code": "USD", "label": "US dollar", "fractionDigits": 2}
  ]
}
```

All four arrays and all item fields are required/non-null. Each `code`/`value` and `label` is a nonempty string; currency `fractionDigits` is a nonnegative integer, initially 2 for every supported currency. Codes/values, not labels, are submitted in requests. Sort countries/currencies by code, departments by canonical value and levels by configured level order. No country-to-currency mapping is implied.

The response must match backend validators and the [employee contract's initial vocabulary](api-employees.md#3-data-definitions-and-validation). Use the same persisted/configured definitions for seeding, creation, salary validation and reports; do not maintain a separate hardcoded frontend list. Unknown response fields can be ignored. Reference data is read-only in this release; changes require configuration/migration and contract/fixture updates, not an administration API.

The UI fetches this after sign-in before enabling forms/filters. On load failure, show Retry and keep dependent controls unavailable; do not substitute stale/guessed options. Backend validation remains authoritative even after the UI fetched reference data. Invalid empty/missing required reference configuration is an operational failure (503), not a successful empty vocabulary.

**Curl:**

```sh
curl --silent --show-error --fail-with-body --include \
  --cookie "$COOKIE_JAR" --cookie-jar "$COOKIE_JAR" \
  --header 'Accept: application/json' \
  "$BASE_URL/api/reference-data"
```

Reuse `BASE_URL` and the authenticated cookie jar from [sign-in setup](api-auth.md#3-shared-curl-setup). This GET is safe to retry and uses the normal no-store policy.

**Errors:** Shared 400 (`VALIDATION_ERROR`), 401, 403 (`ACCESS_DENIED`), 405, 406, 415, 500, 503. Example temporary configuration/dependency failure (`503`):

```json
{
  "code": "SERVICE_UNAVAILABLE",
  "message": "Reference data is temporarily unavailable. Please try again.",
  "requestId": "141d478a-d888-470d-8e8f-b37eea886600",
  "fieldErrors": []
}
```

**Required verification:** Check HR access, no CSRF requirement for GET, exact initial vocabulary/precision, stable ordering, codes versus labels, malformed requests, unavailable configuration and consistency with actual backend validators.
