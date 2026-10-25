# Authentication API Contract

**Version:** 1.0.

This contract defines four authentication APIs for the same-origin AngularJS/Spring Boot application.

See [shared API conventions and the complete endpoint index](api-conventions.md) for cross-contract representation, errors and retry terminology. The sections below define session and CSRF behavior.

## 1. Endpoints and flow

| Method | Path | Access | Success |
|---|---|---|---|
| GET | `/api/auth/csrf` | Public; creates an anonymous session if needed | 200 with CSRF token |
| POST | `/api/auth/login` | Public; valid CSRF token required | 200 with authenticated HR user |
| GET | `/api/auth/me` | Authenticated, enabled HR user | 200 with current user |
| POST | `/api/auth/logout` | Valid CSRF token required; an authenticated user is not required | 204, no body |

Sequence: fetch CSRF token → submit credentials → refresh CSRF token → fetch current user → use the application → submit logout. Fetch a new token before signing in again after logout. First-time and returning HR users with provisioned accounts follow the same sequence.

## 2. Shared conventions

### Transport and sessions

- Production base URL: the deployed HTTPS origin, followed by `/api/auth/...`. The actual deployment URL is not assigned yet.
- Local-development example origin: `http://localhost:8080`. HTTP is limited to local development; its session cookie omits Secure so the example can work. Production requires HTTPS and Secure cookies.
- Session cookie name: `JSESSIONID`; cookie path: `/`; HttpOnly; SameSite=Lax; Secure in production. No persistent cookie lifetime or remember-me login is used. The backend idle timeout is initially 1,800 seconds and is configurable.
- Session identity and authentication state live on the server. The browser sends the cookie automatically for same-origin requests. No Bearer token, Basic Authorization header, or browser-local-storage token is part of this contract.
- Successful authentication rotates the session identifier. Logout invalidates the session and expires the session cookie with matching path/domain attributes.
- Return `Cache-Control: no-store` on all four endpoints, including errors. Never include a password, password hash, session ID, or CSRF token in routine logs. The session ID appears only in its cookie header, not JSON.
- Current-user access also requires the account to remain enabled and retain the HR role. Account revocation must not remain effective only for new logins; reject an existing session after revocation as described under `/me`.

### Headers

| Header | Direction | Rule |
|---|---|---|
| `Accept: application/json` | Request | Recommended for every request; all response bodies are JSON |
| `Content-Type: application/x-www-form-urlencoded` | Login request | Required for the credentials body |
| `X-CSRF-TOKEN: <token>` | Login/logout request | Required; use the latest token from `/csrf` for the same cookie session |
| `Cookie: JSESSIONID=<opaque-value>` | Request | Required for `/me` and for matching the CSRF session; handled by browser/curl cookie jar |
| `Content-Type: application/json` | Response with a body | Returned for success and error JSON; omitted for bodyless 204 |
| `Set-Cookie` | Response | Used when creating/rotating/expiring a session, not guaranteed on every request |
| `Cache-Control: no-store` | Response | Required on successes and errors |
| `X-Request-ID: <server-generated UUID>` | Response | Correlation identifier; matches `requestId` in error JSON |
| `Retry-After: <integer-seconds>` | Rate-limit response | Required for 429; caller must respect the returned delay |

The CSRF token is separate from the authentication cookie. `/csrf` returns the token as JSON; this design does not require a JavaScript-readable CSRF cookie. Keep the token in frontend memory and refresh it after login/logout. Configure Spring's CSRF request handler consistently with the token representation returned by `/csrf`.

### Common data rules

- UTF-8 text; timestamps, if present, use ISO-8601 with an explicit offset. No timestamps are required in the current success schemas.
- User IDs are opaque strings; roles are strings and currently contain only `HR`. Frontend role names omit Spring's internal `ROLE_` prefix.
- No endpoint accepts path or query parameters. Unexpected query parameters and unexpected login form fields return 400. GET requests and logout accept no request body.
- Required email/password form fields must occur exactly once. Normalize email by trimming and case-insensitive comparison. Do not trim, case-fold, or otherwise change the password.
- Work email must be nonblank, pass backend email-format validation, and contain at most 254 characters after trimming. Password length is 1–128 characters; whitespace is significant. Apply the same limits during HR provisioning. Accept at most 4,096 bytes in the encoded login body and return 413 when exceeded.
- Unknown JSON fields are irrelevant to login because JSON login bodies are not supported. Malformed form encoding is a 400 error; JSON login is a 415 error.

### Error envelope

```json
{
  "code": "VALIDATION_ERROR",
  "message": "Check the required sign-in fields.",
  "requestId": "14eb6d79-4a43-450d-a239-31b714634c5a",
  "fieldErrors": [
    {"field": "email", "code": "REQUIRED", "message": "Work email is required."}
  ]
}
```

`code`, `message`, `requestId`, and `fieldErrors` are always present in error JSON. `fieldErrors` is an empty array for non-field errors. Shared conventions allow optional `details` only for a documented code; authentication errors do not use it. Do not echo rejected passwords, credentials, tokens, or internal exception details. Clients branch on `code`. This server-generated `requestId` traces one HTTP attempt and is distinct from a salary operation's client-generated `Idempotency-Key`.

| HTTP status | Code | Meaning |
|---|---|---|
| 400 | `VALIDATION_ERROR` | Missing/duplicate/unknown fields, malformed email, invalid parameters or malformed form |
| 401 | `INVALID_CREDENTIALS` | Unknown account, wrong password or disabled account during login; same message for all |
| 401 | `AUTHENTICATION_REQUIRED` | No authenticated session, including an expired or revoked session |
| 403 | `ACCESS_DENIED` | Valid authenticated identity without HR permission |
| 403 | `CSRF_INVALID` | Missing/invalid CSRF token, including a token from an expired/different session |
| 405 | `METHOD_NOT_ALLOWED` | Unsupported HTTP method; response also includes `Allow` |
| 406 | `NOT_ACCEPTABLE` | Client excludes JSON from acceptable response formats |
| 413 | `PAYLOAD_TOO_LARGE` | Request exceeds the configured body-size limit |
| 415 | `UNSUPPORTED_MEDIA_TYPE` | Login body is not form-encoded, or a body is supplied to a bodyless endpoint |
| 429 | `TOO_MANY_ATTEMPTS` | Configured login attempt limit reached; includes `Retry-After` |
| 500 | `INTERNAL_ERROR` | Unexpected application failure |
| 503 | `SERVICE_UNAVAILABLE` | Temporary authentication/session dependency failure |

Authentication/CSRF filters can reject a request before field validation. A missing or invalid CSRF token on login/logout therefore produces 403 rather than a credential/field result. Validation of credentials and HR membership occurs before returning successful login. If credentials are valid but HR access is absent, return 403 and leave no authenticated application session. Do not redirect API requests to HTML login/error pages.

## 3. Shared curl setup

The following commands target the future local server. They require curl and Python 3, and must not be run until the backend and a provisioned demo HR account exist. Example identities are fictional; no working password is supplied.

```sh
BASE_URL='http://localhost:8080'
COOKIE_JAR='./acme-auth.cookies'
HR_EMAIL='maya.rao@acme.example'
HR_PASSWORD='REPLACE_WITH_PROVISIONED_PASSWORD'
umask 077
```

Use the same cookie jar throughout. Keep its contents private. For a deployed environment, replace `BASE_URL` with its HTTPS origin. Do not disable certificate verification. Supply credentials through your local secret-handling method rather than committing them or publishing command output containing them.

## 4. GET /api/auth/csrf

**Purpose:** Obtain a token valid for state-changing requests in the current session. This endpoint does not authenticate a user or grant HR access. It is reachable before login and after session expiry.

**Parameters/body:** None. **Request headers:** `Accept`; existing session cookie when available.

Example request:

```http
GET /api/auth/csrf HTTP/1.1
Host: localhost:8080
Accept: application/json
```

Example local-development response when a session is created:

```http
HTTP/1.1 200 OK
Content-Type: application/json
Cache-Control: no-store
X-Request-ID: 003d4f22-0244-4d32-a0b8-f860e416bdca
Set-Cookie: JSESSIONID=example-anonymous-session; Path=/; HttpOnly; SameSite=Lax
```

```json
{
  "token": "example-opaque-csrf-token",
  "headerName": "X-CSRF-TOKEN"
}
```

Both fields are required strings. Treat `token` as opaque; neither predict its format nor persist it as a constant. Retain the associated cookie. The response must materialize the token even if Spring uses deferred token creation. Token values may change between responses; always use the most recently retrieved value.

**Curl:**

```sh
curl --silent --show-error --fail-with-body \
  --cookie "$COOKIE_JAR" --cookie-jar "$COOKIE_JAR" \
  --header 'Accept: application/json' \
  --dump-header csrf-response.headers \
  --output csrf-response.json \
  "$BASE_URL/api/auth/csrf"

CSRF_TOKEN=$(python3 -c 'import json; print(json.load(open("csrf-response.json"))["token"])')
```

Only extract the token after a successful curl exit. The response files are local examples, not project artifacts to commit. Run this same command again after successful login and before login following logout.

**Failures:** Common 400, 405, 406, 415, 500 or 503 errors. A missing authentication cookie is not an error; create an anonymous CSRF session when necessary.

## 5. POST /api/auth/login

**Purpose:** Authenticate a provisioned, enabled HR account using work email and password. Use Spring Security's form-login processing with custom JSON success/failure handlers. Configure its username parameter as `email` and its processing URL as this endpoint.

**Parameters:** No path/query parameters. **Headers:** `Accept`, form `Content-Type`, session cookie, and `X-CSRF-TOKEN`.

| Form field | Type | Required | Rule |
|---|---|---|---|
| `email` | String | Yes | Valid provisioned work email, up to 254 characters after trimming; normalized for lookup |
| `password` | String | Yes | 1–128 characters; compare through Spring's password encoder without normalization |

Example encoded request body:

```http
POST /api/auth/login HTTP/1.1
Host: localhost:8080
Accept: application/json
Content-Type: application/x-www-form-urlencoded
Cookie: JSESSIONID=example-anonymous-session
X-CSRF-TOKEN: example-opaque-csrf-token

email=maya.rao%40acme.example&password=REPLACE_WITH_PROVISIONED_PASSWORD
```

Example local-development success headers:

```http
HTTP/1.1 200 OK
Content-Type: application/json
Cache-Control: no-store
X-Request-ID: da2da723-fbf0-4a5a-8ac8-9984c9237238
Set-Cookie: JSESSIONID=example-authenticated-session; Path=/; HttpOnly; SameSite=Lax
```

```json
{
  "user": {
    "id": "hr-0001",
    "email": "maya.rao@acme.example",
    "displayName": "Maya Rao",
    "roles": ["HR"]
  },
  "idleTimeoutSeconds": 1800
}
```

`user` and its four fields are required. `idleTimeoutSeconds` is a positive integer reflecting current configuration, not an absolute expiration time. All authenticated requests may refresh the idle timeout. Success rotates the session ID and clears the old CSRF token. Fetch a fresh token from `/csrf` before another state-changing request. The frontend then opens the directory; the API itself sends no redirect.

**Curl:**

```sh
curl --silent --show-error --fail-with-body --include \
  --request POST \
  --cookie "$COOKIE_JAR" --cookie-jar "$COOKIE_JAR" \
  --header 'Accept: application/json' \
  --header 'Content-Type: application/x-www-form-urlencoded' \
  --header "X-CSRF-TOKEN: $CSRF_TOKEN" \
  --data-urlencode "email=$HR_EMAIL" \
  --data-urlencode "password=$HR_PASSWORD" \
  "$BASE_URL/api/auth/login"
```

After success, repeat the `/csrf` curl command to update `CSRF_TOKEN` using the rotated session. Clear the password from frontend form state after the request completes; do not retry credential submission automatically.

Example incorrect-credentials response (`401`), also used for unknown/disabled accounts:

```json
{
  "code": "INVALID_CREDENTIALS",
  "message": "Email or password is incorrect.",
  "requestId": "98aec158-79ae-4451-b4b3-8ee14c067ce2",
  "fieldErrors": []
}
```

Default attempt policy: after five credential failures for a normalized email/client-IP pair within a rolling 15-minute window, reject further login attempts for that pair until the oldest counted failure leaves the window. Do not count CSRF/format failures as credential failures. Successful login clears that pair's failure counter. Use only a trusted proxy configuration when resolving client IP; do not trust arbitrary forwarded headers. Limits are configurable, and `Retry-After` returns the remaining whole seconds rounded up.

Example rate-limit response (`429`, with `Retry-After: 60` when 60 seconds remain):

```json
{
  "code": "TOO_MANY_ATTEMPTS",
  "message": "Too many sign-in attempts. Please try again later.",
  "requestId": "bf3fd175-a71c-4224-81a7-b7cdd9aa799f",
  "fieldErrors": []
}
```

**Failures:** Common 400, 401 (`INVALID_CREDENTIALS`), 403 (`CSRF_INVALID` or `ACCESS_DENIED`), 405, 406, 413, 415, 429, 500 or 503. Failed authentication must not leave a newly authenticated session. Network uncertainty is resolved with `/me`; do not infer success or blindly repeat login.

## 6. GET /api/auth/me

**Purpose:** Restore frontend user state on page load/refresh and determine whether HR access is still valid. This is not a refresh-token endpoint.

**Parameters/body:** None. **Headers:** `Accept` and authenticated session cookie. CSRF is not required for this read.

```http
GET /api/auth/me HTTP/1.1
Host: localhost:8080
Accept: application/json
Cookie: JSESSIONID=example-authenticated-session
```

Success (`200`, with shared response headers):

```json
{
  "user": {
    "id": "hr-0001",
    "email": "maya.rao@acme.example",
    "displayName": "Maya Rao",
    "roles": ["HR"]
  },
  "idleTimeoutSeconds": 1800
}
```

The schema matches successful login. Password/hash, session ID and CSRF token are never returned here. Read enabled status and current permissions, not only a stale role snapshot from initial login.

**Curl:**

```sh
curl --silent --show-error --fail-with-body --include \
  --cookie "$COOKIE_JAR" --cookie-jar "$COOKIE_JAR" \
  --header 'Accept: application/json' \
  "$BASE_URL/api/auth/me"
```

Missing, expired, invalidated, or disabled-account sessions return `401` with this body:

```json
{
  "code": "AUTHENTICATION_REQUIRED",
  "message": "Please sign in to access the HR workspace.",
  "requestId": "35d0c52e-a226-4f39-88e6-a335f1363cdf",
  "fieldErrors": []
}
```

An enabled authenticated identity that no longer has HR permission returns `403`:

```json
{
  "code": "ACCESS_DENIED",
  "message": "You do not have access to this HR workspace.",
  "requestId": "8c37b746-7297-4f3a-b33b-8819f73c9f54",
  "fieldErrors": []
}
```

On revocation, invalidate the authenticated session; subsequent requests return 401. The frontend clears sensitive in-memory data on access loss and shows sign-in or the access-denied state. It must not automatically replay an interrupted salary write after reauthentication.

**Failures:** Common 400, 401 (`AUTHENTICATION_REQUIRED`), 403 (`ACCESS_DENIED`), 405, 406, 415, 500 or 503. No HTML redirect.

## 7. POST /api/auth/logout

**Purpose:** Invalidate the server-side session and clear authentication. Configure Spring Security's logout filter and a bodyless success handler for this path; do not implement logout by merely hiding the frontend or deleting its user object.

**Parameters/body:** None. **Headers:** `Accept`, session cookie, and valid `X-CSRF-TOKEN`. Omit request `Content-Type` because no body is sent.

```http
POST /api/auth/logout HTTP/1.1
Host: localhost:8080
Accept: application/json
Cookie: JSESSIONID=example-authenticated-session
X-CSRF-TOKEN: example-refreshed-csrf-token
```

Example local-development success:

```http
HTTP/1.1 204 No Content
Cache-Control: no-store
X-Request-ID: c16d22be-f06a-48c9-a832-2780e553fa9e
Set-Cookie: JSESSIONID=; Path=/; Max-Age=0; HttpOnly; SameSite=Lax
```

**Response body:** None, including no empty JSON object. The server invalidates the session and clears the security context and CSRF token. The frontend clears sensitive in-memory state and displays sign-in only after confirmed logout, or after `/me` confirms there is no authenticated session.

**Curl:**

```sh
curl --silent --show-error --fail-with-body --include \
  --request POST \
  --cookie "$COOKIE_JAR" --cookie-jar "$COOKIE_JAR" \
  --header 'Accept: application/json' \
  --header "X-CSRF-TOKEN: $CSRF_TOKEN" \
  "$BASE_URL/api/auth/logout"
```

Use the token refreshed after login. A missing/expired/different-session token returns `403`:

```json
{
  "code": "CSRF_INVALID",
  "message": "Your security token is no longer valid. Refresh it and try again.",
  "requestId": "24cb0296-4775-4593-9ea2-c936a9aa69e2",
  "fieldErrors": []
}
```

If the original session expired or an earlier logout already succeeded, the old CSRF token is also invalid. Fetch `/csrf` again, then retry logout with that token/cookie pair. Logout returns 204 for a valid anonymous CSRF session as well; it does not require an HR role. A replay with the old token is not guaranteed to return 204.

For network failure or 500/503, keep a visible retry option and do not assert that logout succeeded. `/me` can establish whether authentication remains; an unavailable `/me` cannot confirm logout. GET on this API path returns 405 and does not terminate the session or show Spring's default HTML confirmation page.

**Failures:** Common 400, 403 (`CSRF_INVALID`), 405, 406, 415, 500 or 503. A valid anonymous logout does not produce 401.

## 8. Required contract verification

Use the [testing strategy](testing-strategy.md) to verify:

- JSON schemas and shared headers; no HTML redirects, credential leakage, or success bodies on 204.
- Anonymous CSRF issuance, same-session token validation, rotation after login, and fresh-token handling after logout/expiry.
- Form-encoded login, email normalization, unchanged password handling, generic credential failure, non-HR rejection, and rate-limit responses.
- Session-ID rotation, production cookie attributes, account/role revocation, idle expiry, and backend access rejection after logout.
- `/me` restoration on page refresh, safe handling of uncertain authentication/logout outcomes, and no automatic replay of writes.
- All four curl examples against a running local backend with a provisioned fictional account.

## References

- [Spring Security form login](https://docs.spring.io/spring-security/reference/servlet/authentication/passwords/form.html)
- [Spring Security session management](https://docs.spring.io/spring-security/reference/servlet/authentication/session-management.html)
- [Spring Security CSRF and JavaScript integration](https://docs.spring.io/spring-security/reference/servlet/exploits/csrf.html)
- [Spring Security logout](https://docs.spring.io/spring-security/reference/servlet/authentication/logout.html)
