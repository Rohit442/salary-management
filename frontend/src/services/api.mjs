const UUID = /^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/i;
const CODES = /^[A-Z][A-Z_]+$/;

export class ApiError extends Error {
  constructor(message, { status = 0, code = 'REQUEST_FAILED', fieldErrors = [], requestId = null,
    retryAfter = null, details, uncertain = false } = {}) {
    super(message);
    this.name = 'ApiError';
    Object.assign(this, { status, code, fieldErrors, requestId, retryAfter, retryAfterSeconds: retryAfter, uncertain });
    if (details) this.details = details;
  }
}

function apiPath(path) {
  if (typeof path !== 'string' || !path.startsWith('/') || path.startsWith('//') || /[\\?#]/.test(path)
      || path.split('/').some(part => part === '.' || part === '..')) {
    throw new TypeError('Use a relative API path and supply query parameters separately.');
  }
  return path.startsWith('/api/') ? path : `/api${path}`;
}

function requestUrl(path, query = {}) {
  const params = new URLSearchParams();
  for (const [key, value] of Object.entries(query)) {
    for (const item of Array.isArray(value) ? value : [value]) {
      if (item !== null && item !== undefined) params.append(key, String(item));
    }
  }
  return path + (params.size ? `?${params}` : '');
}

const invalidResponse = (status, uncertain = false) => new ApiError('The server returned an unexpected response. Please try again.', {
  status, code: 'INVALID_RESPONSE', uncertain
});

function authentication(body, uncertain = false) {
  const user = body?.user;
  if (!user || !['id', 'email', 'displayName'].every(key => typeof user[key] === 'string' && user[key])
      || !Array.isArray(user.roles) || !user.roles.every(role => typeof role === 'string') || !user.roles.includes('HR')
      || !Number.isSafeInteger(body.idleTimeoutSeconds) || body.idleTimeoutSeconds <= 0) {
    throw invalidResponse(200, uncertain);
  }
  return { user: { id: user.id, email: user.email, displayName: user.displayName, roles: [...user.roles] },
    idleTimeoutSeconds: body.idleTimeoutSeconds };
}

function responseError(response, body, write) {
  const defaults = {
    401: ['AUTHENTICATION_REQUIRED', 'Please sign in to continue.'],
    403: ['ACCESS_DENIED', 'You do not have access to this workspace.'],
    429: ['TOO_MANY_ATTEMPTS', 'Too many attempts. Please try again later.'],
    500: ['INTERNAL_ERROR', 'The request could not be completed. Please try again.'],
    503: ['SERVICE_UNAVAILABLE', 'The service is temporarily unavailable. Please try again.']
  };
  const [fallbackCode, fallbackMessage] = defaults[response.status] || ['REQUEST_FAILED', 'The request could not be completed.'];
  const code = typeof body?.code === 'string' && CODES.test(body.code) ? body.code : fallbackCode;
  const message = typeof body?.code === 'string' && CODES.test(body.code) && typeof body.message === 'string'
    && body.message.trim() ? body.message : fallbackMessage;
  const fieldErrors = Array.isArray(body?.fieldErrors) ? body.fieldErrors
    .filter(field => field && ['field', 'code', 'message'].every(key => typeof field[key] === 'string'))
    .map(({ field, code, message }) => ({ field, code, message })) : [];
  const correlation = response.headers.get('X-Request-ID') || body?.requestId;
  const delay = response.headers.get('Retry-After');
  const retryAfter = /^\d+$/.test(delay || '') && Number.isSafeInteger(Number(delay)) ? Number(delay) : null;
  const details = code === 'VERSION_CONFLICT' && body?.details
    && ['expectedVersion', 'currentVersion'].every(key => Number.isSafeInteger(body.details[key]) && body.details[key] >= 0)
    ? { expectedVersion: body.details.expectedVersion, currentVersion: body.details.currentVersion } : undefined;
  return new ApiError(message, { status: response.status, code, fieldErrors, retryAfter, details,
    requestId: typeof correlation === 'string' && UUID.test(correlation) ? correlation : null,
    uncertain: write && response.status >= 500 });
}

/** Owns only in-memory identity/CSRF state; the browser owns the HttpOnly session cookie. */
export function createApiClient(fetchImpl = globalThis.fetch, onAccessLost = () => {}) {
  let user = null, csrfToken = null, idleTimeoutSeconds = null, revision = 0;
  const clear = () => { user = null; csrfToken = null; idleTimeoutSeconds = null; revision++; };
  const applyAuthentication = result => { user = result.user; idleTimeoutSeconds = result.idleTimeoutSeconds; };
  const stateChanged = () => new ApiError('Your sign-in state changed. Please try again.', { code: 'CLIENT_STATE_CHANGED' });

  async function request(path, { method = 'GET', query, body, headers = {}, expectedStatus, protectedRequest = true } = {}) {
    const write = method !== 'GET';
    const requestRevision = revision;
    let response;
    try {
      response = await fetchImpl(requestUrl(path, query), {
        method, credentials: 'same-origin', cache: 'no-store', redirect: 'error',
        headers: { Accept: 'application/json', ...headers }, ...(body === undefined ? {} : { body })
      });
    } catch {
      throw new ApiError('Unable to reach the server. Check your connection and try again.', {
        code: 'NETWORK_ERROR', uncertain: write
      });
    }
    let data = null, validJson = false;
    if (response.status !== 204) {
      let text;
      try { text = await response.text(); }
      catch {
        if (response.ok) throw new ApiError('The connection ended before the response arrived. Please try again.', {
          code: 'NETWORK_ERROR', uncertain: write
        });
      }
      if ((response.headers.get('Content-Type') || '').toLowerCase().includes('application/json')) {
        try { data = JSON.parse(text); validJson = true; } catch { /* Never expose an HTML or malformed body. */ }
      }
    }
    if (!response.ok) {
      const error = responseError(response, data, write);
      if (requestRevision === revision && error.code === 'CSRF_INVALID') csrfToken = null;
      if (requestRevision === revision && protectedRequest && ((error.status === 401 && error.code !== 'INVALID_CREDENTIALS')
          || (error.status === 403 && error.code === 'ACCESS_DENIED'))) {
        clear();
        onAccessLost(error);
      }
      throw error;
    }
    if ((expectedStatus && response.status !== expectedStatus) || (response.status !== 204 && !validJson)) {
      throw invalidResponse(response.status, write);
    }
    return data;
  }

  async function fetchCsrf() {
    const started = revision;
    const body = await request('/api/auth/csrf', { expectedStatus: 200, protectedRequest: false });
    if (started !== revision) throw stateChanged();
    if (typeof body?.token !== 'string' || !body.token || body.headerName !== 'X-CSRF-TOKEN') throw invalidResponse(200);
    csrfToken = body.token;
    return csrfToken;
  }

  async function restore() {
    const started = revision;
    try {
      const result = authentication(await request('/api/auth/me', { expectedStatus: 200 }));
      if (started !== revision) return null;
      applyAuthentication(result);
      return user;
    } catch (error) {
      if (error.status === 401 && error.code !== 'INVALID_CREDENTIALS') return null;
      throw error;
    }
  }

  async function login(email, password) {
    clear();
    const started = revision;
    await fetchCsrf();
    let result;
    try {
      result = authentication(await request('/api/auth/login', {
        method: 'POST', body: new URLSearchParams({ email, password }).toString(), expectedStatus: 200,
        headers: { 'Content-Type': 'application/x-www-form-urlencoded', 'X-CSRF-TOKEN': csrfToken }, protectedRequest: false
      }), true);
    } catch (error) {
      csrfToken = null;
      if (!error.uncertain) throw error;
      try {
        result = authentication(await request('/api/auth/me', { expectedStatus: 200, protectedRequest: false }));
      } catch { throw error; }
    }
    if (started !== revision) throw stateChanged();
    applyAuthentication(result);
    csrfToken = null;
    // Login is already confirmed. A failed token refresh must not invent a failed sign-in.
    try { await fetchCsrf(); } catch { csrfToken = null; }
    return user;
  }

  async function logout() {
    const started = ++revision;
    if (!csrfToken) await fetchCsrf();
    try {
      await request('/api/auth/logout', { method: 'POST', expectedStatus: 204,
        headers: { 'X-CSRF-TOKEN': csrfToken }, protectedRequest: false });
      clear();
    } catch (error) {
      if (!error.uncertain) throw error;
      try {
        const result = authentication(await request('/api/auth/me', { expectedStatus: 200 }));
        if (started === revision) applyAuthentication(result);
      } catch (check) {
        if (check.status === 401 && check.code !== 'INVALID_CREDENTIALS') return;
      }
      throw error;
    }
  }

  async function get(path, query) {
    const target = apiPath(path);
    return request(target, { query, protectedRequest: !['/api/auth/csrf', '/api/auth/login', '/api/auth/logout'].includes(target) });
  }

  async function send(method, path, body, { idempotencyKey } = {}) {
    const target = apiPath(path);
    method = method.toUpperCase();
    if (!['POST', 'PUT', 'PATCH', 'DELETE'].includes(method)) throw new TypeError('Use get() for reads.');
    const started = revision;
    if (!csrfToken) await fetchCsrf();
    if (started !== revision) throw stateChanged();
    const headers = { 'X-CSRF-TOKEN': csrfToken };
    if (body !== undefined) headers['Content-Type'] = 'application/json';
    if (idempotencyKey) headers['Idempotency-Key'] = idempotencyKey;
    return request(target, { method, headers, body: body === undefined ? undefined : JSON.stringify(body) });
  }

  return { get user() { return user; }, get csrfToken() { return csrfToken; },
    get idleTimeoutSeconds() { return idleTimeoutSeconds; }, restore, login, logout, get, send,
    references: () => get('/reference-data'), clear };
}
