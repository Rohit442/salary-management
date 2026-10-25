import { expect, test, type Page } from '@playwright/test';
import { employee, fixtureApi, open, type FixtureState } from './frontend-fixtures';

type Mode = 'add' | 'edit';
type Field = { label: string; key: string; value: string; select?: boolean; optionLabel?: string; addOnly?: boolean };
const fields: Field[] = [
  { label: 'Employee ID', key: 'employeeId', value: 'EMP-009999', addOnly: true },
  { label: 'Full name', key: 'fullName', value: 'Alex Morgan' },
  { label: 'Work email', key: 'workEmail', value: 'alex.morgan@acme.example' },
  { label: 'Country', key: 'countryCode', value: 'IN', select: true, optionLabel: 'India' },
  { label: 'Department', key: 'department', value: 'Engineering', select: true },
  { label: 'Job title', key: 'jobTitle', value: 'Software Engineer' },
  { label: 'Job level', key: 'jobLevel', value: 'L3', select: true },
  { label: 'Annual base salary', key: 'annualBaseSalary', value: '1250000.50', addOnly: true },
  { label: 'Currency', key: 'currency', value: 'INR', select: true, optionLabel: 'INR — Indian rupee', addOnly: true },
  { label: 'Joining date', key: 'joiningDate', value: '2026-04-01' },
  { label: 'Initial salary effective from', key: 'initialSalaryEffectiveFrom', value: '2026-04-01', addOnly: true },
];
const control = (page: Page, field: Field) => page.getByRole(field.select ? 'combobox' : 'textbox', { name: field.label, exact: true });
const save = (page: Page, mode: Mode) => page.getByRole('button', { name: mode === 'add' ? 'Save employee' : 'Save changes', exact: true });
const writes = (state: FixtureState, mode: Mode) => state.requests.filter(request =>
  request.method === (mode === 'add' ? 'POST' : 'PUT') && request.path === (mode === 'add' ? '/api/employees' : `/api/employees/${employee.employeeId}/details`));

async function setField(page: Page, field: Field, value: string) {
  if (field.select) await control(page, field).selectOption({ label: value ? (field.optionLabel || value) : `Choose ${field.label.toLowerCase()}` });
  else await control(page, field).fill(value);
}

async function readyForm(page: Page, mode: Mode) {
  const state = await fixtureApi(page);
  await open(page, mode === 'add' ? '/employees/new' : `/employees/${employee.employeeId}/edit`);
  await expect(page.getByRole('heading', { name: mode === 'add' ? 'Add employee' : 'Edit employee', exact: true })).toBeVisible();
  for (const field of fields.filter(field => mode === 'add' || !field.addOnly)) await setField(page, field, field.value);
  return state;
}

async function expectBlocked(page: Page, state: FixtureState, mode: Mode) {
  await save(page, mode).click();
  await expect(page.getByRole('alert')).toContainText('Complete the required fields and correct any invalid values.');
  expect(writes(state, mode)).toHaveLength(0);
}

async function expectSaved(page: Page, state: FixtureState, mode: Mode) {
  await save(page, mode).click();
  await expect(page.getByText(mode === 'add' ? 'Employee created successfully.' : 'Employee details saved.', { exact: true })).toBeVisible();
  expect(writes(state, mode)).toHaveLength(1);
  return JSON.parse(writes(state, mode)[0].body!);
}

test.describe('Employee form input validation', () => {
  for (const mode of ['add', 'edit'] as const) {
    for (const field of fields.filter(field => mode === 'add' || !field.addOnly)) {
      test(`${mode}: ${field.label} is required and accepts a corrected value`, async ({ page }) => {
        const state = await readyForm(page, mode);
        await setField(page, field, '');
        await expectBlocked(page, state, mode);
        await setField(page, field, field.value);
        const payload = await expectSaved(page, state, mode);
        expect(payload[field.key]).toBe(field.value);
      });
      if (!field.select) {
        test(`${mode}: ${field.label} rejects whitespace-only input`, async ({ page }) => {
          const state = await readyForm(page, mode);
          await setField(page, field, '   ');
          await expectBlocked(page, state, mode);
          await setField(page, field, field.value);
          await expectSaved(page, state, mode);
        });
      }
    }
  }

  const malformed = [
    { label: 'Employee ID', values: ['EMP-09999', 'EMP-1234567', 'emp-123456', '2026-10-26'] },
    { label: 'Work email', values: ['alex', 'alex@', 'alex acme@example.com'] },
    { label: 'Annual base salary', values: ['-1', '1,000', '1e6', 'abc', '1.'] },
    { label: 'Joining date', values: ['26/10/2026', '2026-1-01'] },
    { label: 'Initial salary effective from', values: ['26/10/2026', '2026-1-01'] },
  ];
  for (const { label, values } of malformed) {
    const field = fields.find(field => field.label === label)!;
    test(`add: ${label} rejects malformed values and recovers after correction`, async ({ page }) => {
      const state = await readyForm(page, 'add');
      for (const value of values) {
        await setField(page, field, value);
        await expectBlocked(page, state, 'add');
      }
      await setField(page, field, field.value);
      await expectSaved(page, state, 'add');
    });
  }

  for (const mode of ['add', 'edit'] as const) {
    for (const label of ['Full name', 'Job title']) {
      const field = fields.find(field => field.label === label)!;
      for (const [kind, character] of [['ASCII', 'A'], ['supplementary Unicode', '𐐀']]) {
        test(`${mode}: ${label} counts ${kind} characters and preserves an overlong draft for correction`, async ({ page }) => {
          const state = await readyForm(page, mode);
          const input = control(page, field);
          const error = input.locator('..').locator('.field-error');
          const boundary = character.repeat(200);
          await input.fill(boundary);
          await expect(input).toHaveValue(boundary);
          await input.blur();
          await expect(error).toBeEmpty();
          await input.fill(`${boundary}${character}`);
          await expect(input).toHaveValue(`${boundary}${character}`);
          await input.blur();
          await expect(error).toContainText('200 characters');
          await expectBlocked(page, state, mode);
          await input.fill(boundary);
          await input.blur();
          await expect(error).toBeEmpty();
          const payload = await expectSaved(page, state, mode);
          expect(payload[field.key]).toBe(boundary);
        });
      }
    }
  }

  test('Work email accepts 254 ASCII characters and prevents typing beyond that limit', async ({ page }) => {
    const state = await readyForm(page, 'add');
    const field = fields.find(field => field.key === 'workEmail')!;
    const value = `${'a'.repeat(64)}@${'b'.repeat(63)}.${'c'.repeat(63)}.${'d'.repeat(57)}.com`;
    await setField(page, field, `${value}x`);
    await expect(control(page, field)).toHaveValue(value);
    const payload = await expectSaved(page, state, 'add');
    expect(payload.workEmail).toBe(value);
  });

  test('edit: identity and status are read-only and an empty last employment date is sent as null', async ({ page }) => {
    const state = await readyForm(page, 'edit');
    await expect(page.getByRole('textbox', { name: 'Employee ID', exact: true })).toHaveAttribute('readonly', 'readonly');
    await expect(page.getByRole('textbox', { name: 'Employee ID', exact: true })).toHaveValue(employee.employeeId);
    await expect(page.getByRole('textbox', { name: 'Employment status', exact: true })).toHaveAttribute('readonly', '');
    await expect(page.getByRole('textbox', { name: 'Employment status', exact: true })).toHaveValue('Active');
    await expect(page.getByRole('textbox', { name: 'Annual base salary', exact: true })).toHaveCount(0);
    await expect(page.getByRole('combobox', { name: 'Currency', exact: true })).toHaveCount(0);
    await page.getByRole('textbox', { name: 'Last employment date', exact: true }).fill('');
    const payload = await expectSaved(page, state, 'edit');
    expect(payload.lastEmploymentDate).toBeNull();
    expect(payload).not.toHaveProperty('employeeId');
    expect(payload).not.toHaveProperty('employmentStatus');
    expect(payload).not.toHaveProperty('annualBaseSalary');
    expect(payload).not.toHaveProperty('currency');
  });

  test('edit: last employment date rejects malformed input and accepts a corrected optional date', async ({ page }) => {
    const state = await readyForm(page, 'edit');
    const date = page.getByRole('textbox', { name: 'Last employment date', exact: true });
    for (const invalid of ['26/10/2026', '2026-1-01']) {
      await date.fill(invalid);
      await expectBlocked(page, state, 'edit');
    }
    await date.fill('2026-11-12');
    const payload = await expectSaved(page, state, 'edit');
    expect(payload.lastEmploymentDate).toBe('2026-11-12');
  });
});
