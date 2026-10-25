import { expect, test } from '@playwright/test';
import { employee, fixtureApi, open } from './frontend-fixtures';

test('an ordinary employee link opens the profile while preserving an unapplied search draft', async ({ page }) => {
  const alice = { ...employee, employeeId: 'EMP-006001', fullName: 'Alice Martin' };
  const zara = { ...employee, employeeId: 'EMP-006002', fullName: 'Zara Stone' };
  const state = await fixtureApi(page, { directoryRecords: [alice, zara] });
  await open(page, '/employees');
  const search = page.getByLabel('Search by name or employee ID', { exact: true });
  const table = page.getByRole('table');
  await expect(table.getByRole('link', { name: zara.fullName, exact: true })).toBeVisible();

  await search.fill('Alice');
  await table.getByRole('link', { name: zara.fullName, exact: true }).click();
  await expect(page.getByRole('heading', { name: zara.fullName, exact: true })).toBeVisible();
  expect(state.requests.filter(request => request.path === '/api/employees')).toHaveLength(1);

  await page.getByRole('link', { name: 'Employee directory', exact: true }).click();
  await expect(search).toHaveValue('Alice');
  await expect(table.getByRole('link', { name: alice.fullName, exact: true })).toBeVisible();
  await expect(table.getByRole('link', { name: zara.fullName, exact: true })).toBeVisible();
  expect(state.requests.filter(request => request.path === '/api/employees').at(-1)!.query.get('q')).toBeNull();
});
