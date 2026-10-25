# Monthly Base Pay API Contract

**Version:** 1.0. Follow [shared conventions](api-conventions.md), [authentication](api-auth.md), [employee dates](api-employees.md), and [effective-dated salary history](api-salaries.md).

## GET /api/employees/{employeeId}/base-pay-estimate

Calculate base pay for a selected calendar month using the employee's recorded employment dates and the salary applicable to that month. A salary change during a month applies to the whole month. Only joining or leaving partway through the month prorates pay.

### Request

**Access:** Authenticated, enabled HR user. Active and inactive employee records are eligible. **Body:** None. **CSRF:** Not required for GET.

| Input | Type | Rule |
|---|---|---|
| Path `employeeId` | String | Public immutable ID, such as `EMP-004219`; same validation as employee lookup |
| Query `month` | String | Required exactly once; valid Gregorian `YYYY-MM`, years `0001`–`9999` |
| Header `Accept` | Media type | Recommend `application/json` |
| Header `Cookie` | Cookie | Required `JSESSIONID` for the HR session |

Reject unknown, repeated, blank, or malformed query parameters with 400. Dates, salary amounts, employee versions, and calculation rules come from stored data; callers supply only the employee ID and month.

```http
GET /api/employees/EMP-004219/base-pay-estimate?month=2026-11 HTTP/1.1
Host: localhost:8080
Accept: application/json
Cookie: JSESSIONID=example-authenticated-session
```

### Calculation rules

1. Read employment dates, initial salary baseline, effective-dated changes, and employee version from one consistent database snapshot. Determine `asOfDate` from the server's UTC business date.
2. Intersect the selected month with the inclusive interval from `joiningDate` to `lastEmploymentDate`. A null end date is open-ended. Weekends and holidays count as calendar days. An employee joining and leaving on the same date has one eligible day.
3. If there are no eligible days, return 200 with zero eligible days, `salaryBasis: null`, and zero base pay. Do not reject solely because salary coverage starts later.
4. Salary coverage starts at the first day of the initial salary's effective month, clipped to joining date. If any eligible days precede that coverage, return 409 `SALARY_HISTORY_INCOMPLETE`; do not fill gaps with today's salary or zero.
5. Select the initial salary or salary change with the greatest effective date on or before the month's last day. Changes must follow the chronological validation in the salary contract. For multiple changes within one month, the latest effective date wins for the entire month. Use effective dates, not audit recording timestamps.
6. Calculate `annualBaseSalary × eligibleDays / (12 × daysInMonth)`. Keep the multiplication and division exact until rounding the final result HALF_UP to the currency's configured precision. An implementation may use a rational numerator/denominator and perform one final scaled division. Do not calculate from the rounded monthly display value.
7. Future months use the latest recorded applicable salary and known employment dates; set `isFutureMonth: true`. Past estimates use the salary selected for that month. New backdated salary changes or corrected employment dates can change a later estimate request.

Inactivation does not change the employment interval or disable this endpoint. Annual compensation reports continue to use their existing active-employee rules.

### Success response

Standalone example: employee `EMP-004219` joined April 1, 2026, with annual base salary INR 1,200,000. A raise to INR 1,800,000 took effect October 15, 2026. Their inclusive last employment date is November 12, 2026. As of October 25, the November estimate is INR 60,000 for 12 of 30 days.

```http
HTTP/1.1 200 OK
Content-Type: application/json
Cache-Control: no-store
X-Request-ID: 4ce91992-b714-40ad-84ae-941bd5f48d15
```

```json
{
  "employeeId": "EMP-004219",
  "employeeVersion": 5,
  "month": "2026-11",
  "currency": "INR",
  "prorationBasis": "CALENDAR_DAYS",
  "joiningDate": "2026-04-01",
  "lastEmploymentDate": "2026-11-12",
  "salaryHistoryFrom": "2026-04-01",
  "daysInMonth": 30,
  "eligibleDays": 12,
  "salaryBasis": {
    "source": "SALARY_CHANGE",
    "changeId": "chg-c791d6f4-138a-4cf3-839e-a6b543f289c8",
    "effectiveDate": "2026-10-15",
    "annualBaseSalary": "1800000.00",
    "monthlyBasePay": "150000.00"
  },
  "estimatedBasePay": "60000.00",
  "isFutureMonth": true,
  "asOfDate": "2026-10-25",
  "generatedAt": "2026-10-25T08:00:00Z"
}
```

All shown fields are required. `lastEmploymentDate` may be null. `salaryBasis` is null only when `eligibleDays` is zero; otherwise it contains all five shown fields. `source` is `INITIAL` or `SALARY_CHANGE`; `changeId` is null for `INITIAL` and the immutable change ID otherwise. `effectiveDate` identifies the selected record's actual effective date, including a mid-month date. Its rate applies to that whole month.

`salaryHistoryFrom` is the coverage start defined above. `employeeVersion` identifies the consistent employee snapshot used; this read does not increment it. `daysInMonth` is 28, 29, 30, or 31, and `eligibleDays` is between zero and that value. Amounts are decimal strings in `currency`; `monthlyBasePay` is annual salary divided by 12, rounded for display. `estimatedBasePay` is independently calculated from the annual amount and rounded once. `generatedAt` is a UTC instant; `asOfDate` is its UTC calendar date. `isFutureMonth` compares the selected month with that date's month. Use `Cache-Control: no-store`; estimates do not persist a pay run, salary change, or version increment.

Expected values for this fixture:

| Month | Applicable annual salary | Eligible days | Estimated base pay |
|---|---|---|---|
| September 2026 | INR 1,200,000.00 | 30 / 30 | INR 100,000.00 |
| October 2026 | INR 1,800,000.00, effective October 15 | 31 / 31 | INR 150,000.00 |
| November 2026 | INR 1,800,000.00 | 12 / 30 | INR 60,000.00 |
| December 2026 | No employment in month | 0 / 31 | INR 0.00 |

For December, retain the same employment/coverage metadata, set `month` to `2026-12`, `daysInMonth` to 31, `eligibleDays` to 0, `salaryBasis` to null, and `estimatedBasePay` to `"0.00"`.

### Errors

All errors use the shared JSON envelope and cache/tracing headers. Authentication and authorization precede data access.

| Status | Code | Condition |
|---|---|---|
| 400 | `VALIDATION_ERROR` | Invalid ID, missing/malformed/repeated month, or unknown parameter |
| 401 | `AUTHENTICATION_REQUIRED` | Missing, expired, or revoked session |
| 403 | `ACCESS_DENIED` | Authenticated identity lacks enabled HR access |
| 404 | `EMPLOYEE_NOT_FOUND` | Valid employee ID does not exist |
| 409 | `SALARY_HISTORY_INCOMPLETE` | Eligible employment days precede recorded salary coverage |
| 405 | `METHOD_NOT_ALLOWED` | Unsupported method; include `Allow: GET` |
| 406 | `NOT_ACCEPTABLE` | Client excludes JSON |
| 415 | `UNSUPPORTED_MEDIA_TYPE` | Body supplied on this bodyless GET |
| 500 | `INTERNAL_ERROR` | Unexpected calculation/data failure |
| 503 | `SERVICE_UNAVAILABLE` | Temporary dependency failure |

Invalid month example (`400`):

```json
{
  "code": "VALIDATION_ERROR",
  "message": "Choose a valid month.",
  "requestId": "6792853e-161e-4af7-b36f-ea4659f323d9",
  "fieldErrors": [
    {"field": "month", "code": "INVALID_FORMAT", "message": "Use YYYY-MM with a valid year and month."}
  ]
}
```

Incomplete history example (`409`), for a different employee whose employment began in February but whose salary baseline begins in April, when March is requested:

```json
{
  "code": "SALARY_HISTORY_INCOMPLETE",
  "message": "Salary history is incomplete for this month.",
  "requestId": "8d033dfc-a216-4933-b5e5-e0bd0f97642f",
  "fieldErrors": []
}
```

Return an error for query/calculation failure rather than displaying a zero estimate. Preserve the selected month in the UI; clear or clearly mark a previous result when its month or employee changes.

### Curl

Reuse `BASE_URL` and the authenticated `COOKIE_JAR` from [sign-in setup](api-auth.md#3-shared-curl-setup).

**Curl:**

```sh
curl --silent --show-error --fail-with-body --include --get \
  --cookie "$COOKIE_JAR" --cookie-jar "$COOKIE_JAR" \
  --header 'Accept: application/json' \
  --data-urlencode 'month=2026-11' \
  "$BASE_URL/api/employees/EMP-004219/base-pay-estimate"
```

GET is safe to retry. Each response may reflect newer salary history or corrected employment dates. Follow the shared bounded-retry and session-recovery rules.

## Required verification

Use independent expected values for the fixture above, a raise on the final day of a month, two raises in one month, a backdated recording, joining/leaving on the same day, inclusive termination, no employment, leap February, 30/31-day months, future end dates, inactive records, incomplete baseline coverage, and currencies with configured precision. For annual salary `"100.00"` and 29 eligible days in a 30-day month, require `"8.06"`; using rounded monthly `"8.33"` would incorrectly produce `"8.05"`. Verify consistent snapshots during concurrent salary/date edits, HR access, query validation, errors, and the curl example against the running backend.
