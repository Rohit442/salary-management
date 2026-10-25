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
  await page.getByRole('searchbox', { name: 'Search by name or employee ID' }).fill(employee.employeeId);
  await page.getByRole('button', { name: 'Apply filters', exact: true }).click();
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
