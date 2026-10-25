# Compensation Reports API Contract

**Version:** 1.0. This endpoint follows [shared conventions](api-conventions.md), [authentication](api-auth.md), and the [reporting rules](requirements-rules.md#reporting-definitions).

## 1. GET /api/reports/compensation

Return current annual base salary metrics for active employees, grouped and filtered by the supported dimensions. Monthly joining/leaving calculations are provided by the [employee base-pay estimate](api-base-pay.md); this report retains its annual metrics and filters.

**Path/body:** None. **Access:** Enabled HR user. **Request headers:** `Accept: application/json` and `Cookie: JSESSIONID=<session>`. No CSRF token is required for this GET. Success is 200 with JSON, `Cache-Control: no-store`, and a fresh `X-Request-ID`.

### Query parameters

| Parameter | Type/default | Rule |
|---|---|---|
| `country` | Repeated optional string | Supported country codes |
| `department` | Repeated optional string | Supported canonical departments |
| `jobLevel` | Repeated optional string | Supported job levels |
| `currency` | Repeated optional string | Supported currency codes |
| `groupBy` | Repeated optional string | Additional dimensions: `country`, `department`, `jobLevel`; currency is always included automatically |

Use the same reference values and case rules as [employee filters](api-employees.md). Match different filter categories with AND and repeated values within a category with OR. Deduplicate values; reject empty/unsupported values, comma-packed lists, unknown parameters and more than 20 supplied values per filter category before deduplication. Repeated `groupBy` supports at most the three distinct additional dimensions; deduplicate repeated values and return canonical order `country`, `department`, `jobLevel`, then `currency`. Explicit `groupBy=currency` is rejected as unsupported because currency is implicit.

No `q`, employment-status override, date range, sort, page or size parameter is defined for this report. All nonempty groups are returned without truncation; the initial reference vocabulary bounds combinations to at most 900 groups. Revisit response limits if that vocabulary expands. Do not silently return only the first groups.

### Response schema and meaning

| Property | Type | Meaning |
|---|---|---|
| `appliedFilters` | Object | Required `country`, `department`, `jobLevel`, `currency` arrays; empty means unrestricted |
| `groupBy` | String array | Effective grouping dimensions including currency |
| `totalEmployees` | Nonnegative integer | All matching active employees across currencies; not a monetary total |
| `currencySummaries` | Array | One summary per matching currency, computed directly from matching employees |
| `groups` | Array | One result per nonempty selected dimension/currency combination |
| `generatedAt` | UTC timestamp string | Response-generation time, not an assertion that data is unchanged afterward |

Each currency summary requires `currency`, `employeeCount`, `totalAnnualBaseSalary`, `averageAnnualBaseSalary`, and `medianAnnualBaseSalary`. Each group requires `dimensions`, `employeeCount` and those three amount fields. `dimensions` contains exactly the effective group keys; the country dimension contains a country code, not a label. Counts are integers; all amounts are decimal strings rounded once to that currency's precision using HALF_UP. Keep full decimal precision for calculations. Aggregate totals may exceed the per-employee input cap and must not be rounded into that cap or overflow an employee-sized storage type.

Each report's counts, currency summaries and groups must use one consistent Oracle snapshot. Calculate overall metrics from matching employee rows; never average group averages or medians. When no additional dimension is requested, `groupBy` is `["currency"]`; groups and currency summaries describe the same per-currency populations in their respective shapes.

Sort `currencySummaries` by currency code ascending. Sort `groups` by currency ascending, then the requested non-currency dimensions in canonical order using canonical value ordering. Sort/deduplicate filter arrays canonically before echoing them. The frontend must use `dimensions.currency` or summary `currency` when displaying money and must not directly compare currencies.

### Example request

```http
GET /api/reports/compensation?country=IN&jobLevel=L4&currency=INR&groupBy=department HTTP/1.1
Host: localhost:8080
Accept: application/json
Cookie: JSESSIONID=example-authenticated-session
```

Example response headers:

```http
HTTP/1.1 200 OK
Content-Type: application/json
Cache-Control: no-store
X-Request-ID: 028db8e1-a891-46d4-84d5-2b5c71703337
```

```json
{
  "appliedFilters": {"country": ["IN"], "department": [], "jobLevel": ["L4"], "currency": ["INR"]},
  "groupBy": ["department", "currency"],
  "totalEmployees": 5,
  "currencySummaries": [
    {
      "currency": "INR",
      "employeeCount": 5,
      "totalAnnualBaseSalary": "7200000.00",
      "averageAnnualBaseSalary": "1440000.00",
      "medianAnnualBaseSalary": "1440000.00"
    }
  ],
  "groups": [
    {
      "dimensions": {"department": "Engineering", "currency": "INR"},
      "employeeCount": 3,
      "totalAnnualBaseSalary": "4320000.00",
      "averageAnnualBaseSalary": "1440000.00",
      "medianAnnualBaseSalary": "1440000.00"
    },
    {
      "dimensions": {"department": "Product", "currency": "INR"},
      "employeeCount": 2,
      "totalAnnualBaseSalary": "2880000.00",
      "averageAnnualBaseSalary": "1440000.00",
      "medianAnnualBaseSalary": "1440000.00"
    }
  ],
  "generatedAt": "2026-10-25T06:00:00Z"
}
```

This standalone fixture has Engineering salaries INR 1,320,000; 1,440,000; 1,560,000 and Product salaries INR 1,200,000; 1,680,000. It does not assume the salary-write example has been applied.

**Curl:**

```sh
curl --silent --show-error --fail-with-body --get \
  --cookie "$COOKIE_JAR" --cookie-jar "$COOKIE_JAR" \
  --header 'Accept: application/json' \
  --data-urlencode 'country=IN' \
  --data-urlencode 'jobLevel=L4' \
  --data-urlencode 'currency=INR' \
  --data-urlencode 'groupBy=department' \
  "$BASE_URL/api/reports/compensation"
```

First complete [authentication curl setup](api-auth.md#3-shared-curl-setup). To compare departments at the same level, keep `jobLevel` fixed and group by department. To request a country/department breakdown, repeat `groupBy=country` and `groupBy=department`. For multiple currencies, repeat `currency` values or omit that filter; each currency still has independent summaries and groups. GET retries may return a newer snapshot.

### Multiple-currency example

For `GET /api/reports/compensation?currency=INR&currency=USD`, a separate fixture with the five INR employees above and two USD employees paid 135,000 and 145,000 returns:

```json
{
  "appliedFilters": {"country": [], "department": [], "jobLevel": [], "currency": ["INR", "USD"]},
  "groupBy": ["currency"],
  "totalEmployees": 7,
  "currencySummaries": [
    {"currency": "INR", "employeeCount": 5, "totalAnnualBaseSalary": "7200000.00", "averageAnnualBaseSalary": "1440000.00", "medianAnnualBaseSalary": "1440000.00"},
    {"currency": "USD", "employeeCount": 2, "totalAnnualBaseSalary": "280000.00", "averageAnnualBaseSalary": "140000.00", "medianAnnualBaseSalary": "140000.00"}
  ],
  "groups": [
    {"dimensions": {"currency": "INR"}, "employeeCount": 5, "totalAnnualBaseSalary": "7200000.00", "averageAnnualBaseSalary": "1440000.00", "medianAnnualBaseSalary": "1440000.00"},
    {"dimensions": {"currency": "USD"}, "employeeCount": 2, "totalAnnualBaseSalary": "280000.00", "averageAnnualBaseSalary": "140000.00", "medianAnnualBaseSalary": "140000.00"}
  ],
  "generatedAt": "2026-10-25T06:00:00Z"
}
```

Only employee counts combine across currencies. There is no global total, average or median monetary amount.

### No matches

Return 200 rather than an error or fabricated monetary zeros. No average/median exists, so no summary/group row is fabricated; the UI displays “No matching compensation data.”

```json
{
  "appliedFilters": {"country": ["IN"], "department": ["Finance"], "jobLevel": ["L6"], "currency": ["INR"]},
  "groupBy": ["department", "currency"],
  "totalEmployees": 0,
  "currencySummaries": [],
  "groups": [],
  "generatedAt": "2026-10-25T06:00:00Z"
}
```

### Errors

Applicable shared statuses: 400, 401, 403 (`ACCESS_DENIED`), 405, 406, 415, 500, 503. Authentication/authorization occurs before report queries. Unsupported references/grouping are 400 validation errors; a supported combination with no employees is a successful empty report.

```json
{
  "code": "VALIDATION_ERROR",
  "message": "Check the report filters and grouping.",
  "requestId": "39e0561d-d029-4c94-80c4-ac617b23f001",
  "fieldErrors": [
    {"field": "groupBy", "code": "UNSUPPORTED_VALUE", "message": "Group by country, department or job level; currency is included automatically."}
  ]
}
```

On query/database failure return the shared 500/503 envelope, not 200 with empty arrays. The client keeps its selected filters and shows an error/retry state. Do not present a previous report as if it were the result of newly applied filters.

## 2. Required verification

Test every grouping combination, shared filter semantics, independent currencies, inactive exclusion, odd/even medians, display rounding, large aggregate totals, consistent snapshots and empty/error distinctions against Oracle using [independently calculated fixtures](testing-strategy.md). Check group counts sum to currency summaries and all currency counts sum to `totalEmployees`; do not require rounded group averages/medians to sum. Verify the curl example against the running backend.
