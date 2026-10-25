import test from 'node:test';
import assert from 'node:assert/strict';
import { ApiError, createApiClient } from '../src/services/api.mjs';

const USER = { id: 'hr-public-id', email: 'maya@acme.example', displayName: 'Maya Rao', roles: ['HR'] };
const AUTH = { user: USER, idleTimeoutSeconds: 1800 };
const ID = '00000000-0000-4000-8000-000000000001';
const json = (body, status = 200, headers = {}) => new Response(JSON.stringify(body), {
  status, headers: { 'Content-Type': 'application/json', ...headers }
});
const csrf = token => json({ token, headerName: 'X-CSRF-TOKEN' });
const failure = (status, code, fields = [], headers = {}) => json({
  code, message: 'A safe server explanation.', requestId: ID, fieldErrors: fields
}, status, { 'X-Request-ID': ID, ...headers });
function fixture(responses) {
  const calls = [], accessLost = [];
  const client = createApiClient(async (url, options) => {
    calls.push({ url, ...options });
    assert.ok(responses.length, `Unexpected request: ${url}`);
    const next = responses.shift();
    if (next instanceof Error) throw next;
    return typeof next === 'function' ? next() : next;
  }, error => accessLost.push(error));
  return { client, calls, accessLost };
}

test('GET serializes repeated filters and uses same-origin session cookies', async () => {
  const { client, calls } = fixture([json({ totalEmployees: 2 })]);
  assert.deepEqual(await client.get('/reports/compensation', {
    country: ['IN', 'US'], department: ['Design & Research'], groupBy: ['department'], empty: [], omitted: null
  }), { totalEmployees: 2 });
  assert.equal(calls.length, 1);
  assert.equal(calls[0].url, '/api/reports/compensation?country=IN&country=US&department=Design+%26+Research&groupBy=department');
  assert.equal(calls[0].credentials, 'same-origin');
  assert.equal(calls[0].cache, 'no-store');
  assert.equal(calls[0].headers.Accept, 'application/json');
  assert.equal(calls[0].body, undefined);
  assert.equal(calls[0].headers['Content-Type'], undefined);
});

test('client cannot send session tokens to an external URL', async () => {
  const { client, calls } = fixture([]);
  await assert.rejects(client.get('https://outside.example/collect'), TypeError);
  await assert.rejects(client.send('POST', '//outside.example/collect', {}), TypeError);
  assert.equal(calls.length, 0);
});

test('reference data is requested from the authoritative endpoint', async () => {
  const data = { countries: [], currencies: [], departments: [], jobLevels: [] };
  const { client, calls } = fixture([json(data)]);
  assert.deepEqual(await client.references(), data);
  assert.equal(calls[0].url, '/api/reference-data');
});

test('restore obtains the user from /me without retaining unexpected sensitive fields', async () => {
  const { client, calls } = fixture([json({ ...AUTH, user: { ...USER, passwordHash: 'must-not-retain' } })]);
  assert.deepEqual(await client.restore(), USER);
  assert.deepEqual(client.user, USER);
  assert.equal(client.idleTimeoutSeconds, 1800);
  assert.equal(calls[0].url, '/api/auth/me');
});

test('anonymous restore returns null and notifies access loss', async () => {
  const { client, accessLost } = fixture([failure(401, 'AUTHENTICATION_REQUIRED')]);
  assert.equal(await client.restore(), null);
  assert.equal(client.user, null);
  assert.equal(accessLost.length, 1);
});

test('login sends form credentials unchanged and refreshes CSRF after authentication', async () => {
  const { client, calls, accessLost } = fixture([csrf('before'), json(AUTH), csrf('after')]);
  assert.deepEqual(await client.login('  MAYA@ACME.EXAMPLE  ', ' pass+word& '), USER);
  assert.deepEqual(calls.map(call => call.url), ['/api/auth/csrf', '/api/auth/login', '/api/auth/csrf']);
  assert.equal(calls[1].method, 'POST');
  assert.equal(calls[1].headers['Content-Type'], 'application/x-www-form-urlencoded');
  assert.equal(calls[1].headers['X-CSRF-TOKEN'], 'before');
  assert.deepEqual([...new URLSearchParams(calls[1].body)], [['email', '  MAYA@ACME.EXAMPLE  '], ['password', ' pass+word& ']]);
  assert.equal(client.csrfToken, 'after');
  assert.equal(accessLost.length, 0);
});

test('invalid credentials do not trigger a protected-page redirect or retry', async () => {
  const { client, calls, accessLost } = fixture([csrf('before'), failure(401, 'INVALID_CREDENTIALS')]);
  await assert.rejects(client.login(USER.email, 'wrong'), error => error instanceof ApiError && error.code === 'INVALID_CREDENTIALS');
  assert.equal(client.user, null);
  assert.equal(calls.length, 2);
  assert.equal(accessLost.length, 0);
});

test('confirmed login remains signed in when the replacement CSRF request fails', async () => {
  const { client, calls } = fixture([csrf('before'), json(AUTH), new Error('offline'), csrf('new'), json({ saved: true })]);
  assert.deepEqual(await client.login(USER.email, 'password'), USER);
  assert.deepEqual(client.user, USER);
  assert.equal(client.csrfToken, null);
  await client.send('PUT', '/employees/EMP-000001/details', { version: 3 });
  assert.equal(calls[3].url, '/api/auth/csrf');
  assert.equal(calls[4].headers['X-CSRF-TOKEN'], 'new');
});

test('uncertain login uses /me to confirm identity without resubmitting credentials', async () => {
  const { client, calls } = fixture([csrf('before'), new Error('connection lost'), json(AUTH), csrf('after')]);
  assert.deepEqual(await client.login(USER.email, 'password'), USER);
  assert.deepEqual(calls.map(call => call.url), ['/api/auth/csrf', '/api/auth/login', '/api/auth/me', '/api/auth/csrf']);
});

test('uncertain login does not invent a signed-in user when /me is unavailable', async () => {
  const { client, accessLost } = fixture([csrf('before'), new Error('connection lost'), failure(503, 'SERVICE_UNAVAILABLE')]);
  await assert.rejects(client.login(USER.email, 'password'), error => error.code === 'NETWORK_ERROR' && error.uncertain);
  assert.equal(client.user, null);
  assert.equal(client.csrfToken, null);
  assert.equal(accessLost.length, 0);
});

test('writes preserve monetary/date strings, versions and caller-owned idempotency keys', async () => {
  const payload = { annualBaseSalary: '999999999999999.99', effectiveDate: '2026-10-15', reason: ' Annual review ', version: 3 };
  const { client, calls } = fixture([csrf('token'), json({ changeId: 'saved' }, 201)]);
  await client.send('POST', '/employees/EMP-000001/salary-changes', payload, { idempotencyKey: ID });
  assert.deepEqual(JSON.parse(calls[1].body), payload);
  assert.equal(calls[1].headers['Idempotency-Key'], ID);
  assert.equal(calls[1].headers['Content-Type'], 'application/json');
});

test('validation errors retain safe structured fields, retry delay and correlation ID', async () => {
  const fieldErrors = [{ field: 'annualBaseSalary', code: 'INVALID_PRECISION', message: 'Use two decimal places.' }];
  const { client } = fixture([failure(429, 'TOO_MANY_ATTEMPTS', fieldErrors, { 'Retry-After': '37' })]);
  await assert.rejects(client.get('/reports/compensation'), error => {
    assert.ok(error instanceof ApiError);
    assert.equal(error.status, 429);
    assert.equal(error.requestId, ID);
    assert.equal(error.retryAfter, 37);
    assert.equal(error.retryAfterSeconds, 37);
    assert.deepEqual(error.fieldErrors, fieldErrors);
    return true;
  });
});

test('HTML error bodies and transport exception details are never exposed', async () => {
  const { client } = fixture([new Response('<html>database password secret</html>', { status: 500 }), new Error('secret SQL details')]);
  for (let i = 0; i < 2; i++) {
    await assert.rejects(client.get('/reports/compensation'), error => {
      assert.ok(error instanceof ApiError);
      assert.doesNotMatch(error.message, /password|secret|SQL|html/);
      return true;
    });
  }
});

test('protected access loss clears in-memory state and calls the route callback', async () => {
  const { client, accessLost } = fixture([json(AUTH), failure(403, 'ACCESS_DENIED')]);
  await client.restore();
  await assert.rejects(client.get('/reports/compensation'), error => error.code === 'ACCESS_DENIED');
  assert.equal(client.user, null);
  assert.equal(client.csrfToken, null);
  assert.equal(accessLost.length, 1);
});

test('CSRF rejection preserves the user and requires an explicit write retry with a fresh token', async () => {
  const { client, calls, accessLost } = fixture([json(AUTH), csrf('old'), failure(403, 'CSRF_INVALID'), csrf('fresh'), json({ saved: true })]);
  await client.restore();
  await assert.rejects(client.send('PUT', '/employees/EMP-000001/details', { version: 3 }), error => error.code === 'CSRF_INVALID');
  assert.deepEqual(client.user, USER);
  assert.equal(client.csrfToken, null);
  assert.equal(calls.length, 3);
  assert.equal(accessLost.length, 0);
  await client.send('PUT', '/employees/EMP-000001/details', { version: 3 });
  assert.equal(calls[4].headers['X-CSRF-TOKEN'], 'fresh');
});

test('a failed write is not automatically retried', async () => {
  const { client, calls } = fixture([csrf('token'), failure(503, 'SERVICE_UNAVAILABLE')]);
  await assert.rejects(client.send('POST', '/employees', { employeeId: 'EMP-000001' }), error => error.uncertain);
  assert.equal(calls.length, 2);
});

test('logout sends no body or Content-Type and clears state after 204', async () => {
  const { client, calls } = fixture([json(AUTH), csrf('token'), new Response(null, { status: 204 })]);
  await client.restore();
  await client.logout();
  assert.equal(calls[2].url, '/api/auth/logout');
  assert.equal(calls[2].headers['X-CSRF-TOKEN'], 'token');
  assert.equal(calls[2].headers['Content-Type'], undefined);
  assert.equal(calls[2].body, undefined);
  assert.equal(client.user, null);
  assert.equal(client.csrfToken, null);
});

test('uncertain logout resolves if /me confirms the session ended', async () => {
  const { client, calls } = fixture([json(AUTH), csrf('token'), new Error('offline'), failure(401, 'AUTHENTICATION_REQUIRED')]);
  await client.restore();
  await client.logout();
  assert.equal(client.user, null);
  assert.equal(calls.at(-1).url, '/api/auth/me');
});

for (const reconciliation of ['active', 'unavailable']) {
  test(`uncertain logout preserves the user when /me is ${reconciliation}`, async () => {
    const { client, calls } = fixture([json(AUTH), csrf('token'), new Error('offline'), reconciliation === 'active' ? json(AUTH) : new Error('offline')]);
    await client.restore();
    await assert.rejects(client.logout(), error => error.uncertain);
    assert.deepEqual(client.user, USER);
    assert.equal(calls.filter(call => call.url === '/api/auth/logout').length, 1);
  });
}

test('a delayed restore cannot resurrect user state after clear', async () => {
  let resolve;
  const { client } = fixture([() => new Promise(done => { resolve = done; })]);
  const pending = client.restore();
  client.clear();
  resolve(json(AUTH));
  assert.equal(await pending, null);
  assert.equal(client.user, null);
});

test('an old unauthorized response cannot clear a subsequently confirmed login', async () => {
  let resolve;
  const { client, accessLost } = fixture([json(AUTH), () => new Promise(done => { resolve = done; }),
    csrf('before'), json(AUTH), csrf('after')]);
  await client.restore();
  const pending = client.get('/reports/compensation');
  await client.login(USER.email, 'password');
  resolve(failure(401, 'AUTHENTICATION_REQUIRED'));
  await assert.rejects(pending, error => error.status === 401);
  assert.deepEqual(client.user, USER);
  assert.equal(client.csrfToken, 'after');
  assert.equal(accessLost.length, 0);
});

test('a received 401 still clears access if its response body cannot be read', async () => {
  const unreadable = { status: 401, ok: false, headers: new Headers(), async text() { throw new Error('truncated'); } };
  const { client, accessLost } = fixture([json(AUTH), unreadable]);
  await client.restore();
  await assert.rejects(client.get('/reports/compensation'), error => error.status === 401);
  assert.equal(client.user, null);
  assert.equal(accessLost.length, 1);
});
