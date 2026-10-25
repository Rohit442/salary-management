import { expect, test } from '@playwright/test';
import { formatMoney } from '../frontend/src/util/format.mjs';

// Uses a separate Java process and in-memory H2 database, started by the live config.
test('live sign-in, employee search/detail, currency reports, refresh and logout', async ({ page }) => {
  const browserErrors: string[] = [];
  page.on('pageerror', error => browserErrors.push(error.message));
  await page.goto('/#!/sign-in');
  await page.getByRole('textbox', { name: 'Work email', exact: true }).fill('maya.rao@acme.example');
  await page.getByLabel('Password', { exact: true }).fill('AcmeDev!2026');
  const employeesResponse = page.waitForResponse(response => new URL(response.url()).pathname === '/api/employees');
  await page.getByRole('button', { name: 'Sign in', exact: true }).click();
  const directoryResponse = await employeesResponse;
  expect(directoryResponse.status()).toBe(200);
  const directory = await directoryResponse.json();
  expect(directory.totalElements).toBe(20);
  const employee = directory.items[0];
  await expect(page.getByRole('link', { name: employee.fullName, exact: true })).toBeVisible();
  await page.getByLabel('Search by name or employee ID', { exact: true }).fill(employee.employeeId);
  await page.getByLabel('Search by name or employee ID', { exact: true }).press('Tab');
  await expect(page.locator('tbody tr')).toHaveCount(1);
  await page.getByRole('link', { name: employee.fullName, exact: true }).click();
  await expect(page.getByRole('heading', { name: employee.fullName, exact: true })).toBeVisible();
  await expect(page.getByText(employee.workEmail, { exact: true })).toBeVisible();
  const reportResponse = page.waitForResponse(response => new URL(response.url()).pathname === '/api/reports/compensation');
  await page.getByRole('link', { name: 'Reports', exact: true }).click();
  const liveReport = await reportResponse;
  expect(liveReport.status()).toBe(200);
  const report = await liveReport.json();
  expect(report.totalEmployees).toBeGreaterThan(0);
  expect(report.currencySummaries.length).toBeGreaterThan(1);
  for (const summary of report.currencySummaries) {
    await expect(page.getByText(formatMoney(summary.totalAnnualBaseSalary, summary.currency), { exact: true }).first()).toBeVisible();
  }
  await page.reload();
  await expect(page.getByRole('heading', { name: 'Compensation reports' })).toBeVisible();
  await expect(page.getByText('Maya Rao', { exact: true })).toBeVisible();
  const filteredResponse = page.waitForResponse(response => response.url().includes('/api/reports/compensation?') && response.url().includes('country=IN'));
  await page.getByRole('button', { name: 'Country', exact: true }).click();
  await page.getByRole('option', { name: 'India', exact: true }).click();
  await page.keyboard.press('Escape');
  await page.getByRole('button', { name: 'Apply filters', exact: true }).click();
  const filtered = await filteredResponse;
  expect(filtered.status()).toBe(200);
  expect((await filtered.json()).appliedFilters.country).toEqual(['IN']);
  await expect(page.getByRole('status').filter({ hasText: 'Loading' })).toHaveCount(0);
  await page.screenshot({ path: 'test-results/frontend-live-report.png', fullPage: false });
  await page.getByRole('button', { name: 'Sign out', exact: true }).click();
  await expect(page.getByRole('heading', { name: 'Sign in to ACME', exact: true })).toBeVisible();
  const signedOut = await page.request.get('/api/auth/me');
  expect(signedOut.status()).toBe(401);
  expect(browserErrors).toEqual([]);
});

test('live name suggestions open a profile and retain the unchanged directory search', async ({ page }, testInfo) => {
  const queries: URLSearchParams[] = [];
  page.on('request', request => {
    const url = new URL(request.url());
    if (url.pathname === '/api/employees') queries.push(url.searchParams);
  });
  await page.goto('/#!/sign-in');
  await page.getByLabel('Work email', { exact: true }).fill('maya.rao@acme.example');
  await page.getByLabel('Password', { exact: true }).fill('AcmeDev!2026');
  const initialResponse = page.waitForResponse(reply => new URL(reply.url()).pathname === '/api/employees');
  await page.getByRole('button', { name: 'Sign in', exact: true }).click();
  const directory = await (await initialResponse).json();
  expect(directory.size).toBe(100);
  const first = directory.items[0];
  const prefix = first.fullName.slice(0, 2).toLowerCase();
  const input = page.getByLabel('Search by name or employee ID', { exact: true });
  const lookup = page.waitForResponse(reply => new URL(reply.url()).pathname === '/api/employees/suggestions');
  await input.fill(prefix);
  const response = await lookup;
  expect(response.status()).toBe(200);
  const matches = await response.json();
  expect(matches.length).toBeGreaterThan(0);
  expect(matches.length).toBeLessThanOrEqual(8);
  for (const match of matches) {
    expect(Object.keys(match).sort()).toEqual(['employeeId', 'fullName']);
    expect(match.fullName.toLowerCase().startsWith(prefix)).toBe(true);
  }
  const options = page.getByRole('listbox', { name: 'Employee suggestions' }).getByRole('option');
  await expect(options).toHaveText(matches.map((match: { fullName: string }) => match.fullName));
  expect(queries).toHaveLength(1);
  await page.screenshot({ path: testInfo.outputPath('live-directory-suggestions.png'), fullPage: false });
  await options.first().click();
  await expect(page.getByRole('heading', { name: matches[0].fullName, exact: true })).toBeVisible();
  expect(queries).toHaveLength(1);
  await page.goBack();
  await expect(input).toHaveValue(prefix);
  await expect(page.locator('tbody tr')).toHaveCount(20);
  expect(queries.at(-1)!.get('q')).toBeNull();
  await input.fill(first.fullName.slice(1));
  await input.press('Enter');
  await expect(page.getByRole('table').getByRole('link', { name: first.fullName, exact: true })).toBeVisible();
  await expect.poll(() => queries.at(-1)!.get('q')).toBe(first.fullName.slice(1));
});

test('live suggestions respect all applied directory filters', async ({ page }) => {
  await page.goto('/#!/sign-in');
  await page.getByLabel('Work email', { exact: true }).fill('maya.rao@acme.example');
  await page.getByLabel('Password', { exact: true }).fill('AcmeDev!2026');
  const initial = page.waitForResponse(reply => new URL(reply.url()).pathname === '/api/employees');
  await page.getByRole('button', { name: 'Sign in', exact: true }).click();
  const directory = await (await initial).json();
  const target = directory.items[0];
  const refs = await (await page.request.get('/api/reference-data')).json();
  const selections = [
    ['Country', 'country', target.countryCode, refs.countries.find((value: { code: string }) => value.code === target.countryCode).label],
    ['Department', 'department', target.department, refs.departments.find((value: { value: string }) => value.value === target.department).label],
    ['Job level', 'jobLevel', target.jobLevel, target.jobLevel],
    ['Currency', 'currency', target.currency, refs.currencies.find((value: { code: string }) => value.code === target.currency).label],
  ];
  for (const [label, key, value, option] of selections) {
    const control = page.locator(`md-select[aria-label^="${label}"]`);
    await control.click();
    await page.getByRole('option', { name: option, exact: true }).click();
    await page.keyboard.press('Escape');
    await expect(control).toBeFocused();
    const filtered = page.waitForResponse(reply => new URL(reply.url()).pathname === '/api/employees' && new URL(reply.url()).searchParams.get(key) === value);
    await page.getByRole('heading', { name: 'Employee directory', exact: true }).click();
    expect((await filtered).status()).toBe(200);
  }
  const lookup = page.waitForResponse(reply => new URL(reply.url()).pathname === '/api/employees/suggestions');
  const prefix = target.fullName.slice(0, 2);
  await page.getByLabel('Search by name or employee ID', { exact: true }).fill(prefix);
  const response = await lookup;
  for (const [, key, value] of selections) expect(new URL(response.url()).searchParams.getAll(key)).toEqual([value]);
  expect(response.status()).toBe(200);
  const matches = await response.json();
  const expected = directory.items.filter((record: typeof target) => record.fullName.toLowerCase().startsWith(prefix.toLowerCase()) &&
    record.countryCode === target.countryCode && record.department === target.department &&
    record.jobLevel === target.jobLevel && record.currency === target.currency).slice(0, 8);
  expect(matches).toEqual(expected.map(({ employeeId, fullName }: typeof target) => ({ employeeId, fullName })));
  await expect(page.getByRole('listbox', { name: 'Employee suggestions' }).getByRole('option')).toHaveText(expected.map((record: typeof target) => record.fullName));
});

test('live salary history shows the immutable initial salary before and after a salary change', async ({ page }) => {
  await page.goto('/#!/sign-in');
  await page.getByLabel('Work email', { exact: true }).fill('maya.rao@acme.example');
  await page.getByLabel('Password', { exact: true }).fill('AcmeDev!2026');
  const directoryResponse = page.waitForResponse(reply => new URL(reply.url()).pathname === '/api/employees');
  await page.getByRole('button', { name: 'Sign in', exact: true }).click();
  const directory = await (await directoryResponse).json();
  const employee = directory.items.find((record: { employmentStatus: string }) => record.employmentStatus === 'ACTIVE');
  await page.getByRole('link', { name: employee.fullName, exact: true }).click();
  const firstHistoryResponse = page.waitForResponse(reply => new URL(reply.url()).pathname === `/api/employees/${employee.employeeId}/salary-changes`);
  await page.getByRole('link', { name: /View salary history/ }).click();
  const firstHistory = await (await firstHistoryResponse).json();
  const baseline = page.locator('.history-baseline');
  await expect(baseline.locator('strong')).toHaveText(`${formatMoney(employee.annualBaseSalary, employee.currency)} / year`);
  expect(firstHistory.initialSalary).toEqual({
    annualBaseSalary: employee.annualBaseSalary, currency: employee.currency,
    effectiveDate: employee.initialSalaryEffectiveFrom, recordedAt: employee.createdAt,
    actor: { id: expect.any(String), displayName: 'Demo Data Seed' },
  });
  await expect(baseline).toContainText('Actor: Demo Data Seed');
  await expect(baseline).not.toContainText('Effective from —');
  await expect(baseline).not.toContainText('Recorded —');
  await expect(page.getByText('No salary changes recorded. The initial salary is preserved above.', { exact: true })).toBeVisible();

  await page.getByRole('link', { name: /Employee details/ }).click();
  await page.getByRole('link', { name: 'Update salary', exact: true }).click();
  const [whole, fraction] = employee.annualBaseSalary.split('.');
  const newAmount = `${BigInt(whole) + 100n}.${fraction}`;
  await page.getByRole('textbox', { name: 'Annual base salary', exact: true }).fill(newAmount);
  await page.getByRole('textbox', { name: 'Reason for change', exact: true }).fill('Baseline preservation check');
  await page.getByRole('textbox', { name: 'Effective from', exact: true }).fill(new Date().toISOString().slice(0, 10));
  await page.getByRole('button', { name: 'Save salary', exact: true }).click();
  await expect(page.getByRole('heading', { name: 'Employee details', exact: true })).toBeVisible();
  const nextHistoryResponse = page.waitForResponse(reply => new URL(reply.url()).pathname === `/api/employees/${employee.employeeId}/salary-changes`);
  await page.getByRole('link', { name: /View salary history/ }).click();
  const nextHistory = await (await nextHistoryResponse).json();
  expect(nextHistory.initialSalary).toEqual(firstHistory.initialSalary);
  await expect(baseline.locator('strong')).toHaveText(`${formatMoney(employee.annualBaseSalary, employee.currency)} / year`);
  await expect(page.locator('.history-summary .salary-value')).toHaveText(formatMoney(newAmount, employee.currency));
  await expect(page.getByRole('row').filter({ hasText: 'Baseline preservation check' })).toContainText(formatMoney(newAmount, employee.currency));
});
