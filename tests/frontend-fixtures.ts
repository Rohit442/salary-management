import { type Page, type Route } from '@playwright/test';

// These tests isolate frontend behavior with contract fixtures. They are not
// evidence that the employee APIs exist or that backend persistence succeeds.
const frontendUrl = process.env.FRONTEND_URL || 'http://127.0.0.1:5173';
export const email = 'maya.rao@acme.example';
export const password = 'AcmeDev!2026';
export const user = { id: 'hr-fixture-maya', email, displayName: 'Maya Rao', roles: ['HR'] };
export const identity = { user, idleTimeoutSeconds: 1800 };
export const employee = {
  employeeId: 'EMP-004218', fullName: 'Priya Sharma', workEmail: 'priya.sharma@acme.example',
  countryCode: 'IN', department: 'Engineering', jobTitle: 'Senior Software Engineer', jobLevel: 'L4',
  employmentStatus: 'ACTIVE', annualBaseSalary: '1320000.00', currency: 'INR', version: 3,
  createdAt: '2026-04-01T03:45:00Z', updatedAt: '2026-10-25T05:00:00Z', joiningDate: '2026-04-01',
  lastEmploymentDate: null as string | null, initialSalaryEffectiveFrom: '2026-04-01', salaryEffectiveFrom: '2026-04-01',
};
export const references = {
  countries: [
    { code: 'DE', label: 'Germany' }, { code: 'GB', label: 'United Kingdom' }, { code: 'IN', label: 'India' },
    { code: 'SG', label: 'Singapore' }, { code: 'US', label: 'United States' },
  ],
  departments: ['Design', 'Engineering', 'Finance', 'HR', 'Operations', 'Product'].map(value => ({ value, label: value === 'HR' ? 'Human Resources' : value })),
  jobLevels: ['L1', 'L2', 'L3', 'L4', 'L5', 'L6'].map(value => ({ value, label: value })),
  currencies: [
    { code: 'EUR', label: 'Euro', fractionDigits: 2 }, { code: 'GBP', label: 'Pound sterling', fractionDigits: 2 },
    { code: 'INR', label: 'Indian rupee', fractionDigits: 2 }, { code: 'SGD', label: 'Singapore dollar', fractionDigits: 2 },
    { code: 'USD', label: 'US dollar', fractionDigits: 2 },
  ],
};
const summaries = [
  { currency: 'INR', employeeCount: 5, totalAnnualBaseSalary: '7200000.00', averageAnnualBaseSalary: '1440000.00', medianAnnualBaseSalary: '1440000.00' },
  { currency: 'USD', employeeCount: 2, totalAnnualBaseSalary: '280000.00', averageAnnualBaseSalary: '140000.00', medianAnnualBaseSalary: '140000.00' },
];
export type CapturedRequest = { path: string; method: string; query: URLSearchParams; body: string | null; headers: Record<string, string> };
export type FixtureState = {
  signedIn: boolean; rejectLogin: boolean; reportMode: 'data' | 'empty' | 'failure' | 'expired';
  referenceFailure: boolean; logoutUncertain: boolean; salaryConflict: boolean; salaryUncertain: boolean; employeeReadFailure: boolean; employeeVersion: number; historyWithChange: boolean; estimateFailure: boolean; records: Record<string, typeof employee>; requests: CapturedRequest[];
  directoryRecords?: Array<typeof employee>; suggestionsFailure: boolean;
  suggestionResponseGate?: (prefix: string) => Promise<void>;
};
function failure(code: string, message: string) {
  return { code, message, requestId: '00000000-0000-4000-8000-000000000001', fieldErrors: [] };
}
function matchesDirectoryFilters(record: typeof employee, query: URLSearchParams) {
  const fields = { country: 'countryCode', department: 'department', jobLevel: 'jobLevel', currency: 'currency' } as const;
  return Object.entries(fields).every(([key, property]) => !query.getAll(key).length || query.getAll(key).includes(record[property]));
}
async function json(route: Route, status: number, body?: unknown) {
  await route.fulfill({ status, ...(status === 204 ? {} : { contentType: 'application/json', body: JSON.stringify(body) }),
    headers: { 'Cache-Control': 'no-store', 'X-Request-ID': '00000000-0000-4000-8000-000000000001' } });
}
export async function fixtureApi(page: Page, initial: Partial<FixtureState> = {}) {
  const state: FixtureState = {
    signedIn: true, rejectLogin: false, reportMode: 'data', referenceFailure: false, logoutUncertain: false, salaryConflict: false, salaryUncertain: false, employeeReadFailure: false, employeeVersion: 3, historyWithChange: false, estimateFailure: false, records: { [employee.employeeId]: { ...employee } },
    requests: [], suggestionsFailure: false, ...initial,
  };
  for (const record of state.directoryRecords || []) state.records[record.employeeId] = { ...record };
  await page.route('**/api/**', async route => {
    const request = route.request();
    const url = new URL(request.url());
    const path = url.pathname;
    const method = request.method();
    state.requests.push({ path, method, query: url.searchParams, body: request.postData(), headers: request.headers() });
    if (path === '/api/auth/me') {
      if (state.logoutUncertain) return json(route, 503, failure('SERVICE_UNAVAILABLE', 'Authentication is temporarily unavailable.'));
      return state.signedIn ? json(route, 200, identity) : json(route, 401, failure('AUTHENTICATION_REQUIRED', 'Please sign in to access the HR workspace.'));
    }
    if (path === '/api/auth/csrf') return json(route, 200, { token: state.signedIn ? 'fixture-authenticated-token' : 'fixture-anonymous-token', headerName: 'X-CSRF-TOKEN' });
    if (path === '/api/auth/login') {
      if (state.rejectLogin) return json(route, 401, failure('INVALID_CREDENTIALS', 'Email or password is incorrect.'));
      state.signedIn = true;
      return json(route, 200, identity);
    }
    if (path === '/api/auth/logout') {
      if (state.logoutUncertain) return json(route, 503, failure('SERVICE_UNAVAILABLE', 'Sign-out is temporarily unavailable. Please try again.'));
      state.signedIn = false;
      return json(route, 204);
    }
    if (path === '/api/reference-data') {
      return state.referenceFailure
        ? json(route, 503, failure('SERVICE_UNAVAILABLE', 'Reference data is temporarily unavailable. Please try again.'))
        : json(route, 200, references);
    }
    if (path === '/api/reports/compensation') {
      if (state.reportMode === 'expired') {
        state.signedIn = false;
        return json(route, 401, failure('AUTHENTICATION_REQUIRED', 'Please sign in to access the HR workspace.'));
      }
      if (state.reportMode === 'failure') return json(route, 503, failure('SERVICE_UNAVAILABLE', 'Reports are temporarily unavailable. Please try again.'));
      const empty = state.reportMode === 'empty';
      const groupBy = [...url.searchParams.getAll('groupBy'), 'currency'];
      const currencySummaries = empty ? [] : summaries;
      return json(route, 200, {
        appliedFilters: Object.fromEntries(['country', 'department', 'jobLevel', 'currency'].map(key => [key, url.searchParams.getAll(key)])),
        groupBy, totalEmployees: empty ? 0 : 7, currencySummaries,
        groups: currencySummaries.map(({ currency, ...metrics }) => ({
          dimensions: { ...Object.fromEntries(groupBy.filter(key => key !== 'currency').map(key => [key, key === 'country' ? (currency === 'INR' ? 'IN' : 'US') : key === 'jobLevel' ? 'L4' : 'Engineering'])), currency }, ...metrics,
        })), generatedAt: '2026-10-25T06:00:00Z',
      });
    }
    if (path === '/api/employees/suggestions' && method === 'GET') {
      const prefix = url.searchParams.get('prefix') || '';
      await state.suggestionResponseGate?.(prefix);
      if (state.suggestionsFailure) return json(route, 503, failure('SERVICE_UNAVAILABLE', 'Suggestions are temporarily unavailable.'));
      const limit = Number(url.searchParams.get('limit') || 8);
      const candidates = state.directoryRecords || Object.values(state.records);
      return json(route, 200, candidates.filter(record => prefix && record.fullName.toLocaleLowerCase().startsWith(prefix.toLocaleLowerCase()) && matchesDirectoryFilters(record, url.searchParams))
        .sort((left, right) => left.fullName.localeCompare(right.fullName) || left.employeeId.localeCompare(right.employeeId))
        .slice(0, limit).map(({ employeeId, fullName }) => ({ employeeId, fullName })));
    }
    if (path === '/api/employees' && method === 'GET') {
      const pageNumber = Number(url.searchParams.get('page') || 0);
      const size = Number(url.searchParams.get('size') || 25);
      if (state.directoryRecords) {
        const q = (url.searchParams.get('q') || '').toLocaleLowerCase();
        const [field, direction] = (url.searchParams.get('sort') || 'fullName,asc').split(',');
        const matching = state.directoryRecords.filter(record => (!q || record.fullName.toLocaleLowerCase().includes(q) || record.employeeId.toLocaleLowerCase().includes(q)) &&
          matchesDirectoryFilters(record, url.searchParams))
          .sort((left, right) => (String(left[field as keyof typeof employee]).localeCompare(String(right[field as keyof typeof employee])) || left.employeeId.localeCompare(right.employeeId)) * (direction === 'desc' ? -1 : 1));
        const items = matching.slice(pageNumber * size, (pageNumber + 1) * size);
        const totalPages = Math.ceil(matching.length / size);
        return json(route, 200, { items, page: pageNumber, size, numberOfElements: items.length, totalElements: matching.length,
          totalPages, sort: { field, direction }, hasPrevious: pageNumber > 0, hasNext: pageNumber + 1 < totalPages });
      }
      const noMatches = url.searchParams.get('q') === 'No matching employee';
      const items = noMatches ? [] : [{ ...employee, ...(pageNumber > 0 ? { employeeId: 'EMP-004219', fullName: 'Sam Rivera', workEmail: 'sam.rivera@acme.example' } : {}) }];
      return json(route, 200, { items, page: pageNumber, size, numberOfElements: items.length,
        totalElements: noMatches ? 0 : 101, totalPages: noMatches ? 0 : Math.ceil(101 / size),
        sort: { field: 'fullName', direction: 'asc' }, hasPrevious: pageNumber > 0, hasNext: !noMatches && pageNumber === 0 });
    }
    if (path === '/api/employees' && method === 'POST') {
      const payload = request.postDataJSON();
      const created = { ...employee, ...payload, employmentStatus: 'ACTIVE', lastEmploymentDate: null,
        version: 0, salaryEffectiveFrom: payload.initialSalaryEffectiveFrom,
        createdAt: '2026-10-25T06:00:00Z', updatedAt: '2026-10-25T06:00:00Z' };
      state.records[created.employeeId] = created;
      return json(route, 201, created);
    }
    const employeeMatch = path.match(/^\/api\/employees\/(EMP-[0-9]{6})(?:\/(details|status|salary-changes|base-pay-estimate))?$/);
    const employeeId = employeeMatch?.[1];
    const record = employeeId ? state.records[employeeId] : undefined;
    if (record && employeeMatch && !employeeMatch[2] && method === 'GET') {
      if (state.employeeReadFailure) return json(route, 503, failure('SERVICE_UNAVAILABLE', 'Employee information is temporarily unavailable.'));
      return json(route, 200, { ...record, version: employeeId === employee.employeeId ? state.employeeVersion : record.version });
    }
    if (record && method === 'PUT' && ['details', 'status'].includes(employeeMatch![2])) {
      const payload = request.postDataJSON();
      state.records[employeeId!] = { ...record, ...payload, version: payload.version + 1, updatedAt: '2026-10-25T06:00:00Z' };
      if (employeeId === employee.employeeId) state.employeeVersion = payload.version + 1;
      return json(route, 200, state.records[employeeId!]);
    }
    if (record && employeeMatch![2] === 'salary-changes' && method === 'GET') {
      const items = state.historyWithChange ? [{ changeId: 'chg-fixture-review', employeeId,
        previousAmount: '1320000.00', newAmount: '1452000.00', currency: 'INR', reason: 'Annual compensation review',
        actor: { id: user.id, displayName: user.displayName }, changedAt: '2026-10-25T05:30:00Z', resultingEmployeeVersion: 4, effectiveDate: '2026-10-15' }] : [];
      return json(route, 200, { employeeId, items, page: 0, size: 25, numberOfElements: items.length, totalElements: items.length,
        totalPages: items.length ? 1 : 0, sort: { field: 'changedAt', direction: 'desc' }, hasPrevious: false, hasNext: false,
        initialSalary: { annualBaseSalary: '1320000.00', currency: 'INR', effectiveDate: '2026-04-01',
          recordedAt: '2026-04-01T03:45:00Z', actor: { id: user.id, displayName: user.displayName } } });
    }
    if (record && employeeMatch![2] === 'base-pay-estimate' && method === 'GET') {
      if (state.estimateFailure) return json(route, 409, failure('SALARY_HISTORY_INCOMPLETE', 'Salary history is incomplete for this month.'));
      const month = url.searchParams.get('month');
      const noEmployment = month === '2026-12';
      return json(route, 200, { employeeId, employeeVersion: state.employeeVersion, month, currency: 'INR',
        joiningDate: '2026-04-01', lastEmploymentDate: '2026-11-12', salaryHistoryFrom: '2026-04-01',
        daysInMonth: noEmployment ? 31 : 30, eligibleDays: noEmployment ? 0 : 12,
        salaryBasis: noEmployment ? null : { source: 'SALARY_CHANGE', changeId: 'chg-fixture-monthly',
          effectiveDate: '2026-10-15', annualBaseSalary: '1800000.00', monthlyBasePay: '150000.00' },
        estimatedBasePay: noEmployment ? '0.00' : '60000.00', isFutureMonth: true,
        asOfDate: '2026-10-25', generatedAt: '2026-10-25T08:00:00Z' });
    }
    if (path === '/api/employees/EMP-004218/salary-changes' && method === 'POST' && state.salaryConflict) {
      state.employeeVersion = 4;
      return json(route, 409, { ...failure('VERSION_CONFLICT', 'This employee changed. Review the latest record before saving again.'), details: { expectedVersion: 3, currentVersion: 4 } });
    }
    if (record && employeeMatch![2] === 'salary-changes' && method === 'POST') {
      if (state.salaryUncertain) return json(route, 503, failure('SERVICE_UNAVAILABLE', 'The salary save outcome is not confirmed. Retry the original change.'));
      const payload = request.postDataJSON();
      state.records[employeeId!] = { ...record, annualBaseSalary: payload.annualBaseSalary, salaryEffectiveFrom: payload.effectiveDate, version: payload.version + 1 };
      state.employeeVersion = payload.version + 1;
      return json(route, 201, { changeId: 'chg-fixture-saved', employeeId, previousAmount: record.annualBaseSalary,
        newAmount: payload.annualBaseSalary, currency: record.currency, reason: payload.reason,
        actor: { id: user.id, displayName: user.displayName }, changedAt: '2026-10-25T05:30:00Z',
        resultingEmployeeVersion: payload.version + 1, effectiveDate: payload.effectiveDate });
    }
    return json(route, 404, failure('EMPLOYEE_NOT_FOUND', 'The requested employee record is unavailable.'));
  });
  return state;
}
export async function open(page: Page, path: string) {
  await page.goto(`${frontendUrl}/#!${path}`);
}
export async function signIn(page: Page) {
  await page.getByLabel('Work email', { exact: true }).fill(email);
  await page.getByLabel('Password', { exact: true }).fill(password);
  await page.getByRole('button', { name: 'Sign in', exact: true }).click();
}
