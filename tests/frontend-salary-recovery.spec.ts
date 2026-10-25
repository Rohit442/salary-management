import { expect, test, type Page } from '@playwright/test';
import { fixtureApi, open } from './frontend-fixtures';

async function enterChange(page: Page) {
  await open(page, '/employees/EMP-004218/salary');
  await page.getByRole('textbox', { name: 'Annual base salary', exact: true }).fill('1452000.00');
  await page.getByRole('textbox', { name: 'Reason for change', exact: true }).fill('Annual compensation review');
  await page.getByRole('textbox', { name: 'Effective from', exact: true }).fill('2026-10-15');
}

const save = (page: Page) => page.getByRole('button', { name: 'Save salary', exact: true });
const retry = (page: Page) => page.getByRole('button', { name: 'Retry original change', exact: true });
const salaryFields = (page: Page) => ['Annual base salary', 'Reason for change', 'Effective from']
  .map(name => page.getByRole('textbox', { name, exact: true }));

test.describe('Salary operation recovery', () => {
  for (const rejection of [
    { name: 'CSRF rejection', status: 403, code: 'CSRF_INVALID', fieldErrors: [] },
    { name: 'rate limiting', status: 429, code: 'TOO_MANY_ATTEMPTS', fieldErrors: [] },
    { name: 'validation before replay', status: 400, code: 'VALIDATION_ERROR',
      fieldErrors: [{ field: 'annualBaseSalary', code: 'INVALID_PRECISION', message: 'Invalid salary precision.' }] },
  ]) {
    test(`preserves the unresolved salary operation after ${rejection.name}`, async ({ page }) => {
      const state = await fixtureApi(page, { salaryUncertain: true });
      const writes: Array<{ body: string | null; key: string }> = [];
      await page.route('**/api/employees/EMP-004218/salary-changes', async route => {
        const request = route.request();
        if (request.method() !== 'POST') return route.fallback();
        writes.push({ body: request.postData(), key: request.headers()['idempotency-key'] });
        if (writes.length === 2) return route.fulfill({ status: rejection.status, json: {
          code: rejection.code, message: 'This retry was rejected.', fieldErrors: rejection.fieldErrors,
        } });
        return route.fallback();
      });
      await enterChange(page);
      await save(page).click();
      await expect(retry(page)).toBeVisible();
      const csrfRequests = state.requests.filter(request => request.path === '/api/auth/csrf').length;
      await retry(page).click();
      await expect(page.getByText('This retry was rejected.', { exact: true })).toBeVisible();
      await expect(retry(page)).toBeVisible();
      await expect(save(page)).toBeDisabled();
      for (const field of salaryFields(page)) await expect(field).toBeDisabled();
      await expect(salaryFields(page)[0]).toHaveValue('1452000.00');
      state.salaryUncertain = false;
      await retry(page).click();
      await expect(page.getByRole('heading', { name: 'Employee details', exact: true })).toBeVisible();
      expect(writes).toHaveLength(3);
      expect(writes[1]).toEqual(writes[0]);
      expect(writes[2]).toEqual(writes[0]);
      if (rejection.code === 'CSRF_INVALID') {
        expect(state.requests.filter(request => request.path === '/api/auth/csrf')).toHaveLength(csrfRequests + 1);
      }
    });
  }

  test('a first-attempt CSRF rejection leaves the draft editable for a fresh save', async ({ page }) => {
    await fixtureApi(page);
    const keys: string[] = [];
    await page.route('**/api/employees/EMP-004218/salary-changes', async route => {
      if (route.request().method() !== 'POST') return route.fallback();
      keys.push(route.request().headers()['idempotency-key']);
      if (keys.length === 1) return route.fulfill({ status: 403, json: {
        code: 'CSRF_INVALID', message: 'Refresh the CSRF token.', fieldErrors: [],
      } });
      return route.fallback();
    });
    await enterChange(page);
    await save(page).click();
    await expect(page.getByText('Refresh the CSRF token.', { exact: true })).toBeVisible();
    await expect(retry(page)).toHaveCount(0);
    await expect(save(page)).toBeEnabled();
    for (const field of salaryFields(page)) await expect(field).toBeEnabled();
    await salaryFields(page)[0].fill('1453000.00');
    await save(page).click();
    await expect(page.getByRole('heading', { name: 'Employee details', exact: true })).toBeVisible();
    expect(keys).toHaveLength(2);
    expect(keys[1]).not.toBe(keys[0]);
  });

  test('a definitive conflict after replay lookup requires review before a new operation', async ({ page }) => {
    const state = await fixtureApi(page, { salaryUncertain: true });
    await enterChange(page);
    await save(page).click();
    await expect(retry(page)).toBeVisible();
    state.salaryConflict = true;
    await retry(page).click();
    await expect(page.getByText('This employee changed. Review the latest record before saving again.', { exact: true })).toBeVisible();
    await expect(retry(page)).toHaveCount(0);
    await expect(save(page)).toBeDisabled();
    await page.getByRole('button', { name: 'Review latest record', exact: true }).click();
    await page.getByRole('button', { name: 'I’ve reviewed this record; keep my draft', exact: true }).click();
    state.salaryConflict = false; state.salaryUncertain = false;
    await save(page).click();
    await expect(page.getByRole('heading', { name: 'Employee details', exact: true })).toBeVisible();
    const writes = state.requests.filter(request => request.method === 'POST' && request.path.endsWith('/salary-changes'));
    expect(writes).toHaveLength(3);
    expect(writes[1].headers['idempotency-key']).toBe(writes[0].headers['idempotency-key']);
    expect(writes[2].headers['idempotency-key']).not.toBe(writes[0].headers['idempotency-key']);
    expect(JSON.parse(writes[2].body!).version).toBe(4);
  });

  test('a service rejection after replay lookup releases an unresolved draft for correction', async ({ page }) => {
    const state = await fixtureApi(page, { salaryUncertain: true });
    const writes: Array<{ body: string | null; key: string }> = [];
    await page.route('**/api/employees/EMP-004218/salary-changes', async route => {
      if (route.request().method() !== 'POST') return route.fallback();
      writes.push({ body: route.request().postData(), key: route.request().headers()['idempotency-key'] });
      if (writes.length === 2) return route.fulfill({ status: 400, json: {
        code: 'VALIDATION_ERROR', message: 'Salary must change.',
        fieldErrors: [{ field: 'annualBaseSalary', code: 'SALARY_UNCHANGED', message: 'Salary must change.' }],
      } });
      return route.fallback();
    });
    await enterChange(page);
    await save(page).click();
    await expect(retry(page)).toBeVisible();
    await retry(page).click();
    await expect(retry(page)).toHaveCount(0);
    await expect(save(page)).toBeEnabled();
    await salaryFields(page)[0].fill('1453000.00');
    state.salaryUncertain = false;
    await save(page).click();
    await expect(page.getByRole('heading', { name: 'Employee details', exact: true })).toBeVisible();
    expect(writes).toHaveLength(3);
    expect(writes[1]).toEqual(writes[0]);
    expect(writes[2].key).not.toBe(writes[0].key);
    expect(JSON.parse(writes[2].body!).annualBaseSalary).toBe('1453000.00');
  });
});
