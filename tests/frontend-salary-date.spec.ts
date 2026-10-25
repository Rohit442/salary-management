import { expect, test, type Page } from '@playwright/test';
import { employee, fixtureApi, open, type FixtureState } from './frontend-fixtures';

const effectiveDate = (page: Page) => page.getByRole('textbox', { name: 'Effective from', exact: true });
const dateError = (page: Page) => page.locator('.effective-date-field .field-error');
const salaryAmount = (page: Page) => page.getByRole('textbox', { name: 'Annual base salary', exact: true });
const amountError = (page: Page) => page.locator('.salary-amount-row .field-error');
const reason = (page: Page) => page.getByRole('textbox', { name: 'Reason for change', exact: true });
const save = (page: Page) => page.getByRole('button', { name: 'Save salary', exact: true });
const salaryWrites = (state: FixtureState) => state.requests.filter(request => request.path.endsWith('/salary-changes') && request.method === 'POST');

async function openSalary(page: Page, record = employee, initial: Partial<FixtureState> = {}) {
  await page.clock.setFixedTime(new Date('2026-10-26T12:00:00Z'));
  const state = await fixtureApi(page, { records: { [record.employeeId]: { ...record } }, ...initial });
  await open(page, `/employees/${record.employeeId}/salary`);
  await expect(page.getByRole('heading', { name: 'Update salary', exact: true })).toBeVisible();
  return state;
}

async function fillSalary(page: Page) {
  await page.getByRole('textbox', { name: 'Annual base salary', exact: true }).fill('1452000.00');
  await reason(page).fill('Annual compensation review');
}

test.describe('Salary field validation', () => {
  test.use({ viewport: { width: 1440, height: 1000 } });

  test('amount waits for blur, associates the inline error, and leaves focus where the user moved it', async ({ page }) => {
    const state = await openSalary(page);
    await expect(amountError(page)).not.toBeVisible();
    await salaryAmount(page).fill('1452000.001');
    await expect(amountError(page)).not.toBeVisible();
    await reason(page).click();
    await expect(amountError(page)).toBeVisible();
    await expect(amountError(page)).toContainText(/up to 2 decimal places/);
    await expect(salaryAmount(page)).toHaveAttribute('aria-invalid', 'true');
    await expect(salaryAmount(page)).toHaveAccessibleDescription(/up to 2 decimal places/);
    await expect(reason(page)).toBeFocused();
    expect(salaryWrites(state)).toHaveLength(0);
  });

  test('rejects empty and invalid decimal amounts after blur', async ({ page }) => {
    await openSalary(page);
    for (const scenario of [
      { name: 'empty', value: '', message: /Enter an annual base salary/ },
      { name: 'letters', value: 'salary', message: /positive decimal amount/ },
      { name: 'commas', value: '1,452,000.00', message: /without commas/ },
      { name: 'a negative number', value: '-1.00', message: /positive decimal amount|greater than zero/ },
      { name: 'zero', value: '0.00', message: /greater than zero/ },
      { name: 'exponent notation', value: '1e6', message: /positive decimal amount/ },
      { name: 'more than two decimal places', value: '1452000.001', message: /up to 2 decimal places/ },
    ]) {
      await test.step(scenario.name, async () => {
        await salaryAmount(page).fill(scenario.value);
        await salaryAmount(page).focus();
        await reason(page).click();
        await expect(amountError(page)).toBeVisible();
        await expect(amountError(page)).toContainText(scenario.message);
        await expect(salaryAmount(page)).toHaveValue(scenario.value);
        await expect(salaryAmount(page)).toHaveAttribute('aria-invalid', 'true');
      });
    }
  });

  for (const value of ['1452000.5', '1452000.50']) {
    test(`clears the amount error after correction and saves ${value}`, async ({ page }) => {
      const state = await openSalary(page);
      await reason(page).fill('Annual compensation review');
      await effectiveDate(page).fill('2026-10-15');
      await salaryAmount(page).fill('1452000.001');
      await reason(page).click();
      await expect(amountError(page)).toContainText(/up to 2 decimal places/);
      await salaryAmount(page).fill(value);
      await reason(page).click();
      await expect(amountError(page)).not.toBeVisible();
      await expect(salaryAmount(page)).not.toHaveAttribute('aria-invalid', 'true');
      await save(page).click();
      await expect(page.getByRole('heading', { name: 'Employee details', exact: true })).toBeVisible();
      expect(salaryWrites(state)).toHaveLength(1);
      expect(JSON.parse(salaryWrites(state)[0].body!).annualBaseSalary).toBe(value);
    });
  }

  for (const submission of ['click', 'Enter'] as const) {
    test(`blocks an invalid amount submitted with ${submission} before its first blur`, async ({ page }) => {
      const state = await openSalary(page);
      await reason(page).fill('Annual compensation review');
      await effectiveDate(page).fill('2026-10-15');
      await salaryAmount(page).fill('1452000.001');
      await expect(amountError(page)).not.toBeVisible();
      if (submission === 'click') await save(page).click();
      else await salaryAmount(page).press('Enter');
      await expect(amountError(page)).toContainText(/up to 2 decimal places/);
      await expect(salaryAmount(page)).toHaveAttribute('aria-invalid', 'true');
      await expect(page.getByRole('heading', { name: 'Update salary', exact: true })).toBeVisible();
      expect(salaryWrites(state)).toHaveLength(0);
    });
  }

  test('waits for blur, associates the inline error, and leaves focus where the user moved it', async ({ page }) => {
    const state = await openSalary(page);
    await expect(dateError(page)).not.toBeVisible();
    await effectiveDate(page).fill('2026-04-01');
    await expect(dateError(page)).not.toBeVisible();
    await reason(page).click();
    await expect(dateError(page)).toBeVisible();
    await expect(dateError(page)).toContainText('must be after 01 Apr 2026');
    await expect(effectiveDate(page)).toHaveAttribute('aria-invalid', 'true');
    await expect(effectiveDate(page)).toHaveAccessibleDescription(/must be after 01 Apr 2026/);
    await expect(reason(page)).toBeFocused();
    expect(salaryWrites(state)).toHaveLength(0);
  });

  for (const scenario of [
    { name: 'earlier than the latest salary date', value: '2026-03-31', message: /must be after 01 Apr 2026/ },
    { name: 'in the future', value: '2026-10-27', message: /on or before today \(26 Oct 2026\)/ },
    { name: 'empty', value: '', message: /Enter an effective date/ },
    { name: 'malformed', value: '26/10/2026', message: /valid date in YYYY-MM-DD format/ },
    { name: 'a nonexistent day', value: '2026-04-31', message: /valid date in YYYY-MM-DD format/ },
    { name: 'a leap day in a non-leap year', value: '2026-02-29', message: /valid date in YYYY-MM-DD format/ },
    { name: 'a leap day in a century not divisible by 400', value: '1900-02-29', message: /valid date in YYYY-MM-DD format/ },
  ]) {
    test(`shows an inline error after blur when the date is ${scenario.name}`, async ({ page }) => {
      await openSalary(page);
      await effectiveDate(page).fill(scenario.value);
      await effectiveDate(page).focus();
      await reason(page).click();
      await expect(dateError(page)).toBeVisible();
      await expect(dateError(page)).toContainText(scenario.message);
      await expect(effectiveDate(page)).toHaveValue(scenario.value);
      await expect(effectiveDate(page)).toHaveAttribute('aria-invalid', 'true');
      await expect(reason(page)).toBeFocused();
    });
  }

  test('rejects a date before the joining date', async ({ page }) => {
    // Isolate the joining bound from the latest-salary bound.
    await openSalary(page, { ...employee, salaryEffectiveFrom: '2026-03-01' });
    await effectiveDate(page).fill('2026-03-31');
    await reason(page).click();
    await expect(dateError(page)).toContainText(/on or after the joining date \(01 Apr 2026\)/);
    await expect(effectiveDate(page)).toHaveAttribute('aria-invalid', 'true');
  });

  test('rejects a date after the last employment date', async ({ page }) => {
    await openSalary(page, { ...employee, lastEmploymentDate: '2026-10-15' });
    await effectiveDate(page).fill('2026-10-16');
    await reason(page).click();
    await expect(dateError(page)).toContainText(/on or before the last employment date \(15 Oct 2026\)/);
    await expect(effectiveDate(page)).toHaveAttribute('aria-invalid', 'true');
  });

  for (const scenario of [
    { name: 'today', date: '2026-10-26', record: employee },
    { name: 'a real leap day', date: '2024-02-29', record: { ...employee, joiningDate: '2024-01-01', initialSalaryEffectiveFrom: '2024-01-01', salaryEffectiveFrom: '2024-01-01' } },
    { name: 'the last employment date', date: '2026-10-15', record: { ...employee, lastEmploymentDate: '2026-10-15' } },
  ]) {
    test(`clears the error after correction and saves ${scenario.name}`, async ({ page }) => {
      const state = await openSalary(page, scenario.record);
      await fillSalary(page);
      await effectiveDate(page).fill('2026-02-30');
      await reason(page).click();
      await expect(dateError(page)).toContainText(/valid date in YYYY-MM-DD format/);
      await effectiveDate(page).fill(scenario.date);
      await reason(page).click();
      await expect(dateError(page)).not.toBeVisible();
      await expect(effectiveDate(page)).not.toHaveAttribute('aria-invalid', 'true');
      await save(page).click();
      await expect(page.getByRole('heading', { name: 'Employee details', exact: true })).toBeVisible();
      expect(salaryWrites(state)).toHaveLength(1);
      expect(JSON.parse(salaryWrites(state)[0].body!)).toMatchObject({ effectiveDate: scenario.date, version: 3 });
    });
  }

  for (const submission of ['click', 'Enter'] as const) {
    test(`blocks an invalid date submitted with ${submission} before its first blur`, async ({ page }) => {
      const state = await openSalary(page);
      await fillSalary(page);
      await effectiveDate(page).fill('2026-04-01');
      await expect(dateError(page)).not.toBeVisible();
      if (submission === 'click') await save(page).click();
      else await effectiveDate(page).press('Enter');
      await expect(dateError(page)).toContainText(/must be after 01 Apr 2026/);
      await expect(effectiveDate(page)).toHaveAttribute('aria-invalid', 'true');
      await expect(page.getByRole('heading', { name: 'Update salary', exact: true })).toBeVisible();
      expect(salaryWrites(state)).toHaveLength(0);
    });
  }

  test('revalidates a preserved date against the latest record after conflict review', async ({ page }) => {
    const state = await openSalary(page, employee, { salaryConflict: true });
    await fillSalary(page);
    await effectiveDate(page).fill('2026-10-15');
    await save(page).click();
    await expect(page.getByText('This employee changed. Review the latest record before saving again.', { exact: true })).toBeVisible();
    state.records[employee.employeeId].salaryEffectiveFrom = '2026-10-20';
    await page.getByRole('button', { name: 'Review latest record', exact: true }).click();
    await expect(page.getByRole('heading', { name: 'Current employee record', exact: true })).toBeVisible();
    await page.getByRole('button', { name: 'I’ve reviewed this record; keep my draft', exact: true }).click();
    await expect(effectiveDate(page)).toHaveValue('2026-10-15');
    await expect(dateError(page)).toContainText(/must be after 20 Oct 2026/);
    await expect(effectiveDate(page)).toHaveAttribute('aria-invalid', 'true');
    state.salaryConflict = false;
    await effectiveDate(page).press('Enter');
    expect(salaryWrites(state)).toHaveLength(1);
    await effectiveDate(page).fill('2026-10-21');
    await reason(page).click();
    await expect(dateError(page)).not.toBeVisible();
    await save(page).click();
    await expect(page.getByRole('heading', { name: 'Employee details', exact: true })).toBeVisible();
    expect(salaryWrites(state)).toHaveLength(2);
    expect(JSON.parse(salaryWrites(state)[1].body!)).toMatchObject({ effectiveDate: '2026-10-21', version: 4 });
  });
});
