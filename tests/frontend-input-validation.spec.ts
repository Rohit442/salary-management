import { expect, test, type Page } from '@playwright/test';
import { email, employee, fixtureApi, open, password, type FixtureState } from './frontend-fixtures';

const requests = (state: FixtureState, path: string) => state.requests.filter(request => request.path === path);
const loginRequests = (state: FixtureState) => requests(state, '/api/auth/login');
const salaryWrites = (state: FixtureState) => state.requests.filter(request => request.path.endsWith('/salary-changes') && request.method === 'POST');
const estimateRequests = (state: FixtureState) => state.requests.filter(request => request.path.endsWith('/base-pay-estimate'));
const filter = (page: Page, label: string) => page.locator(`md-select[aria-label^="${label}"]`);

async function toggleOptions(page: Page, label: string, options: string[]) {
  await filter(page, label).click();
  for (const option of options) await page.getByRole('option', { name: option, exact: true }).click();
  await page.keyboard.press('Escape');
}

async function openSalary(page: Page) {
  await page.clock.setFixedTime(new Date('2026-10-26T12:00:00Z'));
  const state = await fixtureApi(page);
  await open(page, `/employees/${employee.employeeId}/salary`);
  await page.getByRole('textbox', { name: 'Annual base salary', exact: true }).fill('1452000.00');
  await page.getByRole('textbox', { name: 'Effective from', exact: true }).fill('2026-10-15');
  return state;
}

test.describe('Input validation across frontend workflows', () => {
  test.use({ viewport: { width: 1440, height: 1000 } });

  for (const submission of ['click', 'Enter'] as const) {
    test(`sign-in requires both credentials and accepts a correction with ${submission}`, async ({ page }) => {
      const state = await fixtureApi(page, { signedIn: false });
      await open(page, '/sign-in');
      const workEmail = page.getByLabel('Work email', { exact: true });
      const secret = page.getByLabel('Password', { exact: true });
      const submit = async () => submission === 'click'
        ? page.getByRole('button', { name: 'Sign in', exact: true }).click()
        : secret.press('Enter');
      await submit();
      await expect(page.getByText('Enter a valid work email.', { exact: true })).toBeVisible();
      await expect(page.getByText('Password is required.', { exact: true })).toBeVisible();
      expect(loginRequests(state)).toHaveLength(0);
      await workEmail.fill(email);
      await submit();
      await expect(page.getByText('Enter a valid work email.', { exact: true })).toHaveCount(0);
      await expect(page.getByText('Password is required.', { exact: true })).toBeVisible();
      expect(loginRequests(state)).toHaveLength(0);
      await secret.fill(password);
      await submit();
      await expect(page.getByRole('heading', { name: 'Employee directory', exact: true })).toBeVisible();
      expect(loginRequests(state)).toHaveLength(1);
    });
  }

  test('sign-in blocks malformed and whitespace-only email without discarding the password', async ({ page }) => {
    const state = await fixtureApi(page, { signedIn: false });
    await open(page, '/sign-in');
    const workEmail = page.getByLabel('Work email', { exact: true });
    const secret = page.getByLabel('Password', { exact: true });
    await secret.fill(password);
    for (const invalid of ['   ', 'missing-at.example.com', '@example.com', 'person@']) {
      await workEmail.fill(invalid);
      await page.getByRole('button', { name: 'Sign in', exact: true }).click();
      await expect(page.getByText('Enter a valid work email.', { exact: true })).toBeVisible();
      await expect(secret).toHaveValue(password);
      expect(loginRequests(state)).toHaveLength(0);
    }
    await workEmail.fill(email);
    await page.getByRole('button', { name: 'Sign in', exact: true }).click();
    await expect(page.getByRole('heading', { name: 'Employee directory', exact: true })).toBeVisible();
    expect(loginRequests(state)).toHaveLength(1);
  });

  for (const credential of ['x', '  a b  ', '   ']) {
    test(`sign-in preserves a ${credential === 'x' ? 'one-character' : credential.trim() ? 'space-padded' : 'whitespace-only'} password while trimming email`, async ({ page }) => {
      const state = await fixtureApi(page, { signedIn: false });
      await open(page, '/sign-in');
      await page.getByLabel('Work email', { exact: true }).fill(`  ${email}  `);
      await page.getByLabel('Password', { exact: true }).fill(credential);
      await page.getByRole('button', { name: 'Sign in', exact: true }).click();
      await expect(page.getByRole('heading', { name: 'Employee directory', exact: true })).toBeVisible();
      expect(loginRequests(state)).toHaveLength(1);
      expect([...new URLSearchParams(loginRequests(state)[0].body!).entries()]).toEqual([['email', email], ['password', credential]]);
    });
  }

  test('sign-in caps email input and accepts a maximum-length password after correcting an overlong draft', async ({ page }) => {
    const state = await fixtureApi(page, { signedIn: false });
    await open(page, '/sign-in');
    const maximumEmail = `${'a'.repeat(64)}@${'b'.repeat(63)}.${'c'.repeat(63)}.${'d'.repeat(61)}`;
    const maximumPassword = 'p'.repeat(128);
    const workEmail = page.getByLabel('Work email', { exact: true });
    const secret = page.getByLabel('Password', { exact: true });
    await workEmail.fill(maximumEmail);
    await workEmail.pressSequentially('x');
    await expect(workEmail).toHaveValue(maximumEmail);
    await secret.fill(maximumPassword);
    await secret.pressSequentially('x');
    await expect(secret).toHaveValue(`${maximumPassword}x`);
    await page.getByRole('button', { name: 'Sign in', exact: true }).click();
    await expect(page.locator('#password-error')).toContainText('128');
    expect(loginRequests(state)).toHaveLength(0);
    await secret.fill(maximumPassword);
    await expect(secret).toHaveValue(maximumPassword);
    await page.getByRole('button', { name: 'Sign in', exact: true }).click();
    await expect(page.getByRole('heading', { name: 'Employee directory', exact: true })).toBeVisible();
    expect(loginRequests(state)).toHaveLength(1);
    expect([...new URLSearchParams(loginRequests(state)[0].body!).entries()]).toEqual([['email', maximumEmail], ['password', maximumPassword]]);
  });

  test('sign-in accepts 128 Unicode password characters and counts surrounding spaces toward the limit', async ({ page }) => {
    const state = await fixtureApi(page, { signedIn: false });
    await open(page, '/sign-in');
    await page.getByLabel('Work email', { exact: true }).fill(email);
    const secret = page.getByLabel('Password', { exact: true });
    const maximumPassword = '🙂'.repeat(128);
    for (const overlong of [` ${maximumPassword}`, `${maximumPassword} `, ' '.repeat(129)]) {
      await secret.fill(overlong);
      await expect(secret).toHaveValue(overlong);
      await page.getByRole('button', { name: 'Sign in', exact: true }).click();
      await expect(page.locator('#password-error')).toContainText('128');
      expect(loginRequests(state)).toHaveLength(0);
    }
    await secret.fill(maximumPassword);
    await page.getByRole('button', { name: 'Sign in', exact: true }).click();
    await expect(page.getByRole('heading', { name: 'Employee directory', exact: true })).toBeVisible();
    expect(loginRequests(state)).toHaveLength(1);
    expect(new URLSearchParams(loginRequests(state)[0].body!).get('password')).toBe(maximumPassword);
  });

  test('salary requires a nonblank reason and accepts a corrected reason', async ({ page }) => {
    const state = await openSalary(page);
    const reason = page.getByRole('textbox', { name: 'Reason for change', exact: true });
    for (const invalid of ['', '   \n   ']) {
      await reason.fill(invalid);
      await page.getByRole('button', { name: 'Save salary', exact: true }).click();
      await expect(page.getByRole('alert')).toContainText(/reason/i);
      await expect(page.getByRole('heading', { name: 'Update salary', exact: true })).toBeVisible();
      expect(salaryWrites(state)).toHaveLength(0);
    }
    await reason.fill('  Annual review  ');
    await page.getByRole('button', { name: 'Save salary', exact: true }).click();
    await expect(page.getByRole('heading', { name: 'Employee details', exact: true })).toBeVisible();
    expect(salaryWrites(state)).toHaveLength(1);
    expect(JSON.parse(salaryWrites(state)[0].body!).reason).toBe('Annual review');
  });

  for (const length of [1, 1000]) {
    test(`salary accepts a ${length}-character reason${length === 1000 ? ' after correcting an overlong draft' : ''}`, async ({ page }) => {
      const state = await openSalary(page);
      const value = 'r'.repeat(length);
      const reason = page.getByRole('textbox', { name: 'Reason for change', exact: true });
      await reason.fill(value);
      if (length === 1000) {
        await reason.pressSequentially('x');
        await expect(reason).toHaveValue(`${value}x`);
        await page.getByRole('button', { name: 'Save salary', exact: true }).click();
        await expect(reason.locator('..').locator('.field-error')).toContainText(/1,?000/);
        expect(salaryWrites(state)).toHaveLength(0);
        await reason.fill(value);
      }
      await expect(reason).toHaveValue(value);
      await page.getByRole('button', { name: 'Save salary', exact: true }).click();
      await expect(page.getByRole('heading', { name: 'Employee details', exact: true })).toBeVisible();
      expect(salaryWrites(state)).toHaveLength(1);
      expect(JSON.parse(salaryWrites(state)[0].body!).reason).toBe(value);
    });
  }

  test('monthly estimate blocks empty or malformed months and accepts a correction', async ({ page }) => {
    const state = await fixtureApi(page);
    await open(page, `/employees/${employee.employeeId}`);
    const month = page.getByLabel('Selected month', { exact: true });
    for (const invalid of ['', '   ', '2026-00', '2026-13', '2026-1', '2026/01', 'month']) {
      await month.fill(invalid);
      await page.getByRole('button', { name: 'Calculate estimate', exact: true }).click();
      await expect(page.getByRole('alert')).toContainText(/valid month.*YYYY-MM/);
      expect(estimateRequests(state)).toHaveLength(0);
    }
    await month.fill('2026-11');
    await month.press('Enter');
    await expect(page.getByText('INR 60,000.00', { exact: true })).toBeVisible();
    expect(estimateRequests(state)).toHaveLength(1);
    expect(estimateRequests(state)[0].query.get('month')).toBe('2026-11');
  });

  test('monthly estimate rejects year zero before requesting an estimate', async ({ page }) => {
    const state = await fixtureApi(page);
    await open(page, `/employees/${employee.employeeId}`);
    await page.getByLabel('Selected month', { exact: true }).fill('0000-01');
    await page.getByRole('button', { name: 'Calculate estimate', exact: true }).click();
    await expect(page.getByRole('alert')).toContainText(/valid month.*YYYY-MM/);
    expect(estimateRequests(state)).toHaveLength(0);
  });

  test('directory rejects blank and nonnumeric page numbers and recovers after correction', async ({ page }) => {
    const state = await fixtureApi(page);
    await open(page, '/employees');
    const number = page.getByLabel('Page number', { exact: true });
    await expect(number).toHaveValue('1');
    const before = requests(state, '/api/employees').length;
    for (const invalid of ['', '  ', 'two', '-1', '1e2', '9007199254740992']) {
      await number.fill(invalid);
      await number.press('Enter');
      await expect(page.getByRole('alert')).toHaveText('Enter a whole page number from 1 to 2.');
      expect(requests(state, '/api/employees')).toHaveLength(before);
      await expect(page.getByRole('link', { name: 'Priya Sharma', exact: true })).toBeVisible();
    }
    await number.fill('2');
    await page.getByRole('button', { name: 'Go', exact: true }).click();
    await expect(page.getByRole('link', { name: 'Sam Rivera', exact: true })).toBeVisible();
    await expect(page.getByRole('alert')).toHaveCount(0);
    expect(requests(state, '/api/employees')).toHaveLength(before + 1);
    expect(requests(state, '/api/employees').at(-1)!.query.get('page')).toBe('1');
  });

  test('directory search enforces its typing limit and whitespace clears the applied query', async ({ page }) => {
    const state = await fixtureApi(page);
    await open(page, '/employees');
    await expect(page.getByRole('link', { name: 'Priya Sharma', exact: true })).toBeVisible();
    const search = page.getByLabel('Search by name or employee ID', { exact: true });
    const query = 'a'.repeat(100);
    await search.fill(query);
    await search.pressSequentially('x');
    await expect(search).toHaveValue(query);
    await page.getByRole('heading', { name: 'Employee directory', exact: true }).click();
    await expect.poll(() => requests(state, '/api/employees').at(-1)!.query.get('q')).toBe(query);
    await search.fill('   ');
    await page.getByRole('heading', { name: 'Employee directory', exact: true }).click();
    await expect.poll(() => requests(state, '/api/employees').at(-1)!.query.has('q')).toBe(false);
    await expect(page.getByRole('listbox', { name: 'Employee suggestions', exact: true })).toHaveCount(0);
  });

  test('directory search caps inserted and typed text at 100 Unicode characters for blur and Enter', async ({ page }) => {
    const state = await fixtureApi(page);
    await open(page, '/employees');
    await expect(page.getByRole('link', { name: 'Priya Sharma', exact: true })).toBeVisible();
    const search = page.getByLabel('Search by name or employee ID', { exact: true });
    const unicodeQuery = '🙂'.repeat(100);
    await search.fill(`${unicodeQuery}extra`);
    await expect(search).toHaveValue(unicodeQuery);
    await search.pressSequentially('x');
    await expect(search).toHaveValue(unicodeQuery);
    await expect.poll(() => requests(state, '/api/employees/suggestions').at(-1)?.query.get('prefix')).toBe(unicodeQuery);
    await page.getByRole('heading', { name: 'Employee directory', exact: true }).click();
    await expect.poll(() => requests(state, '/api/employees').at(-1)!.query.get('q')).toBe(unicodeQuery);
    const mixedQuery = `Z${'🙂'.repeat(99)}`;
    await search.fill(`${mixedQuery}overflow`);
    await expect(search).toHaveValue(mixedQuery);
    await search.press('Enter');
    await expect.poll(() => requests(state, '/api/employees').at(-1)!.query.get('q')).toBe(mixedQuery);
  });

  test('report selections submit reference codes for every filter and clear back to default grouping', async ({ page }) => {
    const state = await fixtureApi(page);
    await open(page, '/reports');
    await expect(page.getByText(/INR\s+7,200,000\.00/).first()).toBeVisible();
    const reportRequests = () => requests(state, '/api/reports/compensation');
    const before = reportRequests().length;
    const selections: Array<[string, string[], string, string[]]> = [
      ['Country', ['India', 'United States'], 'country', ['IN', 'US']],
      ['Department', ['Engineering', 'Human Resources'], 'department', ['Engineering', 'HR']],
      ['Job level', ['L3', 'L4'], 'jobLevel', ['L3', 'L4']],
      ['Currency', ['Indian rupee', 'US dollar'], 'currency', ['INR', 'USD']],
    ];
    for (const [label, options] of selections) await toggleOptions(page, label, options);
    await toggleOptions(page, 'Group by', ['Country', 'Job level']);
    expect(reportRequests()).toHaveLength(before);
    await page.getByRole('button', { name: 'Apply filters', exact: true }).click();
    await expect.poll(() => reportRequests().length).toBe(before + 1);
    for (const [, , key, values] of selections) expect(reportRequests().at(-1)!.query.getAll(key).sort()).toEqual(values);
    expect(reportRequests().at(-1)!.query.getAll('groupBy').sort()).toEqual(['country', 'department', 'jobLevel']);
    await expect(page.getByRole('columnheader', { name: 'Job level', exact: true }).first()).toBeVisible();
    await page.getByRole('button', { name: 'Clear filters', exact: true }).click();
    await expect.poll(() => reportRequests().length).toBe(before + 2);
    for (const [label, , key] of selections) {
      expect(reportRequests().at(-1)!.query.getAll(key)).toEqual([]);
      await expect(filter(page, label)).not.toContainText(label === 'Country' ? 'India' : label === 'Department' ? 'Engineering' : label === 'Job level' ? 'L3' : 'Indian rupee');
    }
    expect(reportRequests().at(-1)!.query.getAll('groupBy')).toEqual(['department']);
    await expect(filter(page, 'Group by')).toContainText('Department');
  });

  test('report grouping can be empty to show currency-only totals', async ({ page }) => {
    const state = await fixtureApi(page);
    await open(page, '/reports');
    await expect(page.getByText(/INR\s+7,200,000\.00/).first()).toBeVisible();
    await toggleOptions(page, 'Group by', ['Department']);
    await page.getByRole('button', { name: 'Apply filters', exact: true }).click();
    await expect.poll(() => requests(state, '/api/reports/compensation').at(-1)!.query.getAll('groupBy')).toEqual([]);
    await expect(filter(page, 'Group by')).toContainText('Currency only');
    await expect(page.getByText(/INR\s+7,200,000\.00/).first()).toBeVisible();
    await expect(page.getByRole('columnheader', { name: 'Department', exact: true })).toHaveCount(0);
  });
});
