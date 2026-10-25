import { expect, test, type Page, type Response } from '@playwright/test';

// Run only with playwright.frontend-live.config.ts: its Java process owns a disposable
// in-memory H2 database. No route mocks or persistent local employee records are used.
type Employee = {
  employeeId: string; fullName: string; workEmail: string; countryCode: string;
  department: string; jobTitle: string; jobLevel: string; annualBaseSalary: string;
  currency: string; joiningDate: string; initialSalaryEffectiveFrom: string;
  salaryEffectiveFrom: string; lastEmploymentDate: string | null; version: number;
};
type FieldError = { field: string; code: string; message: string };

const futureDate = () => new Date(Date.now() + 3 * 86_400_000).toISOString().slice(0, 10);
const draftFor = (id: string) => ({
  employeeId: id, fullName: `Validation ${id}`, workEmail: `${id.toLowerCase()}@acme.example`,
  countryCode: 'IN', department: 'Engineering', jobTitle: 'Software Engineer', jobLevel: 'L3',
  annualBaseSalary: '120000.00', currency: 'INR', joiningDate: '2024-02-29', initialSalaryEffectiveFrom: '2024-03-01',
});

async function signIn(page: Page) {
  await page.goto('/#!/sign-in');
  await page.getByLabel('Work email', { exact: true }).fill('maya.rao@acme.example');
  await page.getByLabel('Password', { exact: true }).fill('AcmeDev!2026');
  await page.getByRole('button', { name: 'Sign in', exact: true }).click();
  await expect(page.getByRole('heading', { name: 'Employee directory', exact: true })).toBeVisible();
}

async function openCreate(page: Page, id: string) {
  await page.goto('/#!/employees/new');
  await expect(page.getByRole('heading', { name: 'Add employee', exact: true })).toBeVisible();
  const draft = draftFor(id);
  for (const field of ['employeeId', 'fullName', 'workEmail', 'jobTitle', 'annualBaseSalary', 'joiningDate', 'initialSalaryEffectiveFrom'] as const) {
    await page.locator(`#${field}`).fill(draft[field]);
  }
  for (const [field, label] of [['countryCode', 'India'], ['department', 'Engineering'], ['jobLevel', 'L3']]) {
    await page.locator(`#${field}`).selectOption({ label });
  }
  await page.locator('#currency').selectOption({ label: 'INR — Indian Rupee' });
  return draft;
}

async function submit(page: Page, id?: string) {
  const path = id ? `/api/employees/${id}/details` : '/api/employees';
  const reply = page.waitForResponse(response => new URL(response.url()).pathname === path
    && response.request().method() === (id ? 'PUT' : 'POST'));
  await page.getByRole('button', { name: id ? 'Save changes' : 'Save employee', exact: true }).click();
  return reply;
}

async function rejection(page: Page, response: Response, code: string, field?: string, status = 400) {
  expect(response.status()).toBe(status);
  const body = await response.json();
  const issue = body.fieldErrors.find((item: FieldError) => item.code === code && (!field || item.field === field)) as FieldError;
  expect(issue, JSON.stringify(body)).toBeDefined();
  const input = page.locator(`#${issue.field}`);
  await expect(input.locator('..').locator('.field-error')).toContainText(issue.message);
  await expect(page.getByRole('alert')).toContainText(issue.message);
  return body;
}

async function readEmployee(page: Page, id: string): Promise<Employee> {
  const response = await page.request.get(`/api/employees/${id}`);
  expect(response.status()).toBe(200);
  return response.json();
}

async function absent(page: Page, id: string) {
  const response = await page.request.get(`/api/employees/${id}`);
  expect(response.status()).toBe(404);
  expect((await response.json()).code).toBe('EMPLOYEE_NOT_FOUND');
}

async function createSetupEmployee(page: Page, id: string): Promise<Employee> {
  const tokenResponse = await page.request.get('/api/auth/csrf');
  expect(tokenResponse.status()).toBe(200);
  const token = (await tokenResponse.json()).token;
  const created = await page.request.post('/api/employees', {
    headers: { 'X-CSRF-TOKEN': token }, data: draftFor(id),
  });
  expect(created.status()).toBe(201);
  return created.json();
}

async function openEdit(page: Page, id: string) {
  await page.goto(`/#!/employees/${id}/edit`);
  await expect(page.getByRole('heading', { name: 'Edit employee', exact: true })).toBeVisible();
  await expect(page.locator('#fullName')).not.toHaveValue('');
}

test.describe('Add and Edit validation through the real isolated backend', () => {
  test.beforeEach(async ({ page }) => { await signIn(page); });

  const createCases = [
    { name: 'impossible joining date', field: 'joiningDate', value: '2025-02-29', code: 'INVALID_DATE' },
    { name: 'impossible initial salary date', field: 'initialSalaryEffectiveFrom', value: '2024-04-31', code: 'INVALID_DATE' },
    { name: 'future joining date', field: 'joiningDate', value: futureDate, code: 'FUTURE_DATE_NOT_ALLOWED' },
    { name: 'future initial salary date', field: 'initialSalaryEffectiveFrom', value: futureDate, code: 'FUTURE_DATE_NOT_ALLOWED' },
    { name: 'initial salary before joining', field: 'initialSalaryEffectiveFrom', value: '2024-02-28', code: 'INVALID_DATE_ORDER' },
    { name: 'zero salary', field: 'annualBaseSalary', value: '0.00', code: 'POSITIVE_AMOUNT_REQUIRED' },
    { name: 'salary beyond currency precision', field: 'annualBaseSalary', value: '123.456', code: 'INVALID_PRECISION' },
    { name: 'salary beyond fifteen integer digits', field: 'annualBaseSalary', value: '1000000000000000.00', code: 'INVALID_PRECISION' },
  ];

  for (const [index, scenario] of createCases.entries()) {
    test(`Add rejects ${scenario.name}, displays the server field error and stores nothing`, async ({ page }) => {
      const id = `EMP-${971001 + index}`;
      await openCreate(page, id);
      const value = typeof scenario.value === 'function' ? scenario.value() : scenario.value;
      await page.locator(`#${scenario.field}`).fill(value);
      await rejection(page, await submit(page), scenario.code, scenario.field);
      await expect(page.locator(`#${scenario.field}`)).toHaveValue(value);
      await expect(page.getByRole('heading', { name: 'Add employee', exact: true })).toBeVisible();
      await absent(page, id);
    });
  }

  test('Add accepts a corrected leap-day date after a real calendar rejection', async ({ page }) => {
    const id = 'EMP-972000';
    await openCreate(page, id);
    await page.locator('#joiningDate').fill('2025-02-29');
    await rejection(page, await submit(page), 'INVALID_DATE', 'joiningDate');
    await absent(page, id);
    await page.locator('#joiningDate').fill('2024-02-29');
    await page.locator('#initialSalaryEffectiveFrom').fill('2024-02-29');
    expect((await submit(page)).status()).toBe(201);
    await expect(page.getByRole('heading', { name: 'Employee details', exact: true })).toBeVisible();
    expect(await readEmployee(page, id)).toMatchObject({ joiningDate: '2024-02-29', initialSalaryEffectiveFrom: '2024-02-29', version: 0 });
  });

  for (const [index, amount] of ['0.01', '999999999999999.99'].entries()) {
    test(`Add accepts and exactly preserves the valid salary boundary ${amount}`, async ({ page }) => {
      const id = `EMP-${972001 + index}`;
      await openCreate(page, id);
      await page.locator('#annualBaseSalary').fill(amount);
      expect((await submit(page)).status()).toBe(201);
      await expect(page.getByRole('heading', { name: 'Employee details', exact: true })).toBeVisible();
      expect(await readEmployee(page, id)).toMatchObject({ annualBaseSalary: amount, version: 0 });
      const history = await page.request.get(`/api/employees/${id}/salary-changes`);
      expect(history.status()).toBe(200);
      expect((await history.json()).initialSalary.annualBaseSalary).toBe(amount);
    });
  }

  test('Add rejects an existing employee ID without overwriting that employee', async ({ page }) => {
    const before = await readEmployee(page, 'EMP-000001');
    await openCreate(page, 'EMP-973000');
    await page.locator('#employeeId').fill(before.employeeId);
    const body = await rejection(page, await submit(page), 'NOT_UNIQUE', 'employeeId', 409);
    expect(body.code).toBe('EMPLOYEE_ID_EXISTS');
    expect(await readEmployee(page, before.employeeId)).toEqual(before);
    await absent(page, 'EMP-973000');
  });

  test('Add rejects an existing work email and preserves the draft and stored employee', async ({ page }) => {
    const before = await readEmployee(page, 'EMP-000001');
    const id = 'EMP-973001';
    await openCreate(page, id);
    await page.locator('#workEmail').fill(before.workEmail);
    const body = await rejection(page, await submit(page), 'NOT_UNIQUE', 'workEmail', 409);
    expect(body.code).toBe('WORK_EMAIL_EXISTS');
    await expect(page.locator('#employeeId')).toHaveValue(id);
    await absent(page, id);
    expect(await readEmployee(page, before.employeeId)).toEqual(before);
  });

  const editCases = [
    { name: 'impossible joining date', field: 'joiningDate', value: '2025-02-29', code: 'INVALID_DATE' },
    { name: 'impossible last employment date', field: 'lastEmploymentDate', value: '2024-04-31', code: 'INVALID_DATE' },
    { name: 'future joining date', field: 'joiningDate', value: futureDate, code: 'FUTURE_DATE_NOT_ALLOWED' },
    { name: 'last employment date before joining', field: 'lastEmploymentDate', value: '2024-02-28', code: 'INVALID_DATE_ORDER' },
    { name: 'joining after the immutable initial salary', field: 'joiningDate', value: '2024-03-02', code: 'INVALID_DATE_ORDER' },
  ];

  for (const [index, scenario] of editCases.entries()) {
    test(`Edit rejects ${scenario.name} and preserves the stored employee and version`, async ({ page }) => {
      const before = await createSetupEmployee(page, `EMP-${974001 + index}`);
      await openEdit(page, before.employeeId);
      const value = typeof scenario.value === 'function' ? scenario.value() : scenario.value;
      await page.locator(`#${scenario.field}`).fill(value);
      await rejection(page, await submit(page, before.employeeId), scenario.code, scenario.field);
      await expect(page.locator(`#${scenario.field}`)).toHaveValue(value);
      expect(await readEmployee(page, before.employeeId)).toEqual(before);
    });
  }

  test('Edit rejects an end date before an actual salary change without altering employee or history', async ({ page }) => {
    const initial = await createSetupEmployee(page, 'EMP-975000');
    const token = (await (await page.request.get('/api/auth/csrf')).json()).token;
    const changed = await page.request.post(`/api/employees/${initial.employeeId}/salary-changes`, {
      headers: { 'X-CSRF-TOKEN': token, 'Idempotency-Key': '61f5117c-682e-4a1c-9f14-e2ae5c75c130' },
      data: { annualBaseSalary: '130000.00', effectiveDate: '2024-06-01', reason: 'Isolated validation setup', version: initial.version },
    });
    expect(changed.status()).toBe(201);
    const before = await readEmployee(page, initial.employeeId);
    const historyBefore = await (await page.request.get(`/api/employees/${initial.employeeId}/salary-changes`)).json();
    await openEdit(page, before.employeeId);
    await page.locator('#lastEmploymentDate').fill('2024-05-31');
    // The service can attach interval-consistency errors to either employment date.
    await rejection(page, await submit(page, before.employeeId), 'INVALID_DATE_ORDER');
    expect(await readEmployee(page, before.employeeId)).toEqual(before);
    expect(await (await page.request.get(`/api/employees/${initial.employeeId}/salary-changes`)).json()).toEqual(historyBefore);
    await page.locator('#lastEmploymentDate').fill('2024-06-01');
    expect((await submit(page, before.employeeId)).status()).toBe(200);
    expect(await readEmployee(page, before.employeeId)).toMatchObject({ lastEmploymentDate: '2024-06-01', version: before.version + 1 });
    expect(await (await page.request.get(`/api/employees/${initial.employeeId}/salary-changes`)).json()).toEqual(historyBefore);
  });

  test('Edit rejects a duplicate work email, then accepts a correction without changing salary history', async ({ page }) => {
    const before = await createSetupEmployee(page, 'EMP-975001');
    const historyBefore = await (await page.request.get(`/api/employees/${before.employeeId}/salary-changes`)).json();
    const other = await readEmployee(page, 'EMP-000001');
    await openEdit(page, before.employeeId);
    await page.locator('#workEmail').fill(other.workEmail);
    expect((await rejection(page, await submit(page, before.employeeId), 'NOT_UNIQUE', 'workEmail', 409)).code).toBe('WORK_EMAIL_EXISTS');
    expect(await readEmployee(page, before.employeeId)).toEqual(before);
    await page.locator('#workEmail').fill('corrected-validation@acme.example');
    expect((await submit(page, before.employeeId)).status()).toBe(200);
    expect(await readEmployee(page, before.employeeId)).toMatchObject({
      workEmail: 'corrected-validation@acme.example', annualBaseSalary: before.annualBaseSalary,
      initialSalaryEffectiveFrom: before.initialSalaryEffectiveFrom, version: before.version + 1,
    });
    expect(await (await page.request.get(`/api/employees/${before.employeeId}/salary-changes`)).json()).toEqual(historyBefore);
  });

  test('Edit accepts a future end date, then permits clearing it while retaining active status', async ({ page }) => {
    const before = await createSetupEmployee(page, 'EMP-975002');
    await openEdit(page, before.employeeId);
    const future = futureDate();
    await page.locator('#lastEmploymentDate').fill(future);
    expect((await submit(page, before.employeeId)).status()).toBe(200);
    expect(await readEmployee(page, before.employeeId)).toMatchObject({
      lastEmploymentDate: future, employmentStatus: 'ACTIVE', version: before.version + 1,
    });
    await openEdit(page, before.employeeId);
    await page.locator('#lastEmploymentDate').fill('');
    expect((await submit(page, before.employeeId)).status()).toBe(200);
    expect(await readEmployee(page, before.employeeId)).toMatchObject({
      lastEmploymentDate: null, employmentStatus: 'ACTIVE', version: before.version + 2,
    });
  });

  test('Unicode boundary names, titles and salary reasons persist exactly through the real browser', async ({ page }) => {
    const id = 'EMP-976000';
    const fullName = '\u{10400}'.repeat(200);
    const jobTitle = '\u{10428}'.repeat(200);
    const reason = '\u{10401}'.repeat(1000);
    await openCreate(page, id);
    await page.locator('#fullName').fill(fullName);
    await page.locator('#jobTitle').fill(jobTitle);
    await expect(page.locator('#fullName')).toHaveValue(fullName);
    await expect(page.locator('#jobTitle')).toHaveValue(jobTitle);
    const created = await submit(page);
    expect(created.status(), await created.text()).toBe(201);
    expect(created.request().postDataJSON()).toMatchObject({ fullName, jobTitle });
    await expect(page.getByRole('heading', { name: 'Employee details', exact: true })).toBeVisible();
    expect(await readEmployee(page, id)).toMatchObject({ fullName, jobTitle, annualBaseSalary: '120000.00', version: 0 });

    await page.getByRole('link', { name: 'Update salary', exact: true }).click();
    await page.getByRole('textbox', { name: 'Annual base salary', exact: true }).fill('130000.00');
    await page.getByRole('textbox', { name: 'Effective from', exact: true }).fill('2024-06-01');
    await page.getByRole('textbox', { name: 'Reason for change', exact: true }).fill(reason);
    await expect(page.getByRole('textbox', { name: 'Reason for change', exact: true })).toHaveValue(reason);
    const salaryReply = page.waitForResponse(response => new URL(response.url()).pathname === `/api/employees/${id}/salary-changes`
      && response.request().method() === 'POST');
    await page.getByRole('button', { name: 'Save salary', exact: true }).click();
    const changed = await salaryReply;
    expect(changed.status(), await changed.text()).toBe(201);
    expect(changed.request().postDataJSON()).toMatchObject({ reason });
    await expect(page.getByRole('heading', { name: 'Employee details', exact: true })).toBeVisible();
    expect(await readEmployee(page, id)).toMatchObject({ fullName, jobTitle, annualBaseSalary: '130000.00', version: 1 });
    const historyResponse = await page.request.get(`/api/employees/${id}/salary-changes`);
    expect(historyResponse.status()).toBe(200);
    const history = await historyResponse.json();
    expect(history.items).toHaveLength(1);
    expect(history.items[0].reason).toBe(reason);
    expect(history.initialSalary.annualBaseSalary).toBe('120000.00');
    await page.getByRole('link', { name: /View salary history/ }).click();
    await expect(page.locator('.reason-cell')).toHaveText(reason);
  });

});
