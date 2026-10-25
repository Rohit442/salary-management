import { expect, test } from '@playwright/test';
import { fixtureApi, open, signIn, email, password, employee } from './frontend-fixtures';

test.describe('Frontend workflows with isolated contract fixtures', () => {
  test.use({ viewport: { width: 1440, height: 1000 } });

  test('sign-in sends form credentials with CSRF and refreshes the token before entering the directory', async ({ page }) => {
    const state = await fixtureApi(page, { signedIn: false });
    await open(page, '/sign-in');
    await expect(page.getByRole('heading', { name: 'Sign in to ACME', exact: true })).toBeVisible();
    await signIn(page);
    await expect(page.getByRole('heading', { name: 'Employee directory', exact: true })).toBeVisible();
    const login = state.requests.find(request => request.path === '/api/auth/login')!;
    expect(login.method).toBe('POST');
    expect(login.headers['content-type']).toContain('application/x-www-form-urlencoded');
    expect(login.headers['x-csrf-token']).toBe('fixture-anonymous-token');
    expect([...new URLSearchParams(login.body!).entries()]).toEqual([['email', email], ['password', password]]);
    const authSequence = state.requests.filter(request => ['/api/auth/login', '/api/auth/csrf'].includes(request.path)).map(request => request.path);
    expect(authSequence).toEqual(['/api/auth/csrf', '/api/auth/login', '/api/auth/csrf']);
    await expect(page.getByText('Maya Rao', { exact: true })).toBeVisible();
  });

  test('incorrect credentials preserve email, clear the password and stay on sign-in', async ({ page }) => {
    const state = await fixtureApi(page, { signedIn: false, rejectLogin: true });
    await open(page, '/sign-in');
    await signIn(page);
    await expect(page.getByText('Email or password is incorrect.', { exact: true })).toBeVisible();
    await expect(page.getByLabel('Password', { exact: true })).toHaveValue('');
    await expect(page.getByLabel('Work email', { exact: true })).toHaveValue(email);
    await expect(page.getByRole('heading', { name: 'Sign in to ACME', exact: true })).toBeVisible();
    expect(state.requests.filter(request => request.path === '/api/auth/login')).toHaveLength(1);
  });

  test('reports display separate currency totals and restore the session after refresh', async ({ page }) => {
    const state = await fixtureApi(page);
    await open(page, '/reports');
    await expect(page.getByRole('heading', { name: 'Compensation reports', exact: true })).toBeVisible();
    await expect(page.getByText(/INR\s+7,200,000\.00/).first()).toBeVisible();
    await expect(page.getByText(/USD\s+280,000\.00/).first()).toBeVisible();
    await expect(page.getByText(/7,480,000\.00/)).toHaveCount(0);
    await page.reload();
    await expect(page.getByText(/INR\s+7,200,000\.00/).first()).toBeVisible();
    expect(state.requests.filter(request => request.path === '/api/auth/me').length).toBeGreaterThanOrEqual(2);
  });

  test('report filters use backend codes and empty results do not display earlier totals', async ({ page }) => {
    const state = await fixtureApi(page);
    await open(page, '/reports');
    await expect(page.getByText(/INR\s+7,200,000\.00/).first()).toBeVisible();
    await page.getByRole('button', { name: 'Country', exact: true }).click();
    await page.getByRole('option', { name: 'India', exact: true }).click();
    await page.keyboard.press('Escape');
    state.reportMode = 'empty';
    await page.getByRole('button', { name: 'Apply filters', exact: true }).click();
    await expect(page.getByText('No matching compensation data.', { exact: true })).toBeVisible();
    await expect(page.getByText(/7,200,000\.00/)).toHaveCount(0);
    const lastReport = state.requests.filter(request => request.path === '/api/reports/compensation').at(-1)!;
    expect(lastReport.query.getAll('country')).toEqual(['IN']);
    expect(lastReport.query.getAll('groupBy')).not.toContain('currency');
  });

  test('report failure clears stale results and an explicit retry recovers', async ({ page }) => {
    const state = await fixtureApi(page);
    await open(page, '/reports');
    await expect(page.getByText(/INR\s+7,200,000\.00/).first()).toBeVisible();
    state.reportMode = 'failure';
    await page.getByRole('button', { name: 'Apply filters', exact: true }).click();
    await expect(page.getByText('Reports are temporarily unavailable. Please try again.', { exact: true })).toBeVisible();
    await expect(page.getByText(/7,200,000\.00/)).toHaveCount(0);
    state.reportMode = 'data';
    await page.getByRole('button', { name: 'Retry', exact: true }).click();
    await expect(page.getByText(/INR\s+7,200,000\.00/).first()).toBeVisible();
  });

  test('expired access removes report data and requires a new sign-in', async ({ page }) => {
    const state = await fixtureApi(page);
    await open(page, '/reports');
    await expect(page.getByText(/INR\s+7,200,000\.00/).first()).toBeVisible();
    state.reportMode = 'expired';
    await page.getByRole('button', { name: 'Apply filters', exact: true }).click();
    await expect(page.getByRole('heading', { name: 'Sign in to ACME', exact: true })).toBeVisible();
    await expect(page.getByText(/7,200,000\.00/)).toHaveCount(0);
    expect(state.requests.filter(request => request.path === '/api/auth/login')).toHaveLength(0);
  });

  test('sign-out uses a bodyless request with the current token and removes protected data', async ({ page }) => {
    const state = await fixtureApi(page);
    await open(page, '/reports');
    await expect(page.getByText(/INR\s+7,200,000\.00/).first()).toBeVisible();
    await page.getByRole('button', { name: 'Sign out', exact: true }).click();
    await expect(page.getByRole('heading', { name: 'Sign in to ACME', exact: true })).toBeVisible();
    const logout = state.requests.find(request => request.path === '/api/auth/logout')!;
    expect(logout.method).toBe('POST');
    expect(logout.body).toBeNull();
    expect(logout.headers['content-type']).toBeUndefined();
    expect(logout.headers['x-csrf-token']).toBe('fixture-authenticated-token');
    await expect(page.getByText(/7,200,000\.00/)).toHaveCount(0);
  });

  test('an unconfirmed sign-out keeps the signed-in UI and provides a retry', async ({ page }) => {
    const state = await fixtureApi(page);
    await open(page, '/reports');
    await expect(page.getByText(/INR\s+7,200,000\.00/).first()).toBeVisible();
    state.logoutUncertain = true;
    await page.getByRole('button', { name: 'Sign out', exact: true }).click();
    await expect(page.getByText('Sign-out is temporarily unavailable. Please try again.', { exact: true })).toBeVisible();
    await expect(page.getByRole('heading', { name: 'Compensation reports', exact: true })).toBeVisible();
    await expect(page.getByRole('button', { name: 'Sign out', exact: true })).toBeEnabled();
    state.logoutUncertain = false;
    await page.getByRole('button', { name: 'Sign out', exact: true }).click();
    await expect(page.getByRole('heading', { name: 'Sign in to ACME', exact: true })).toBeVisible();
  });

  test('salary conflict preserves the draft and requires explicit review without replaying the write', async ({ page }) => {
    const state = await fixtureApi(page, { salaryConflict: true });
    await open(page, '/employees/EMP-004218/salary');
    await page.getByRole('textbox', { name: 'Annual base salary', exact: true }).fill('1452000.00');
    await page.getByRole('textbox', { name: 'Reason for change', exact: true }).fill('Annual compensation review');
    await page.getByRole('textbox', { name: 'Effective from', exact: true }).fill('2026-10-15');
    await page.getByRole('button', { name: 'Save salary', exact: true }).click();
    await expect(page.getByText('This employee changed. Review the latest record before saving again.', { exact: true })).toBeVisible();
    await expect(page.getByRole('textbox', { name: 'Annual base salary', exact: true })).toHaveValue('1452000.00');
    await expect(page.getByRole('textbox', { name: 'Reason for change', exact: true })).toHaveValue('Annual compensation review');
    await expect(page.getByRole('textbox', { name: 'Effective from', exact: true })).toHaveValue('2026-10-15');
    const write = state.requests.find(request => request.path.endsWith('/salary-changes') && request.method === 'POST')!;
    expect(JSON.parse(write.body!)).toEqual({ annualBaseSalary: '1452000.00', effectiveDate: '2026-10-15', reason: 'Annual compensation review', version: 3 });
    expect(write.headers['idempotency-key']).toMatch(/^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/i);
    await expect(page.getByRole('button', { name: 'Save salary', exact: true })).toBeDisabled();
    state.employeeReadFailure = true;
    await page.getByRole('button', { name: 'Review latest record', exact: true }).click();
    await expect(page.getByText('Employee information is temporarily unavailable.', { exact: true })).toBeVisible();
    await expect(page.getByRole('button', { name: 'Save salary', exact: true })).toBeDisabled();
    state.employeeReadFailure = false;
    await page.getByRole('button', { name: 'Review latest record', exact: true }).click();
    await expect(page.getByRole('heading', { name: 'Current employee record', exact: true })).toBeVisible();
    await expect(page.getByRole('textbox', { name: 'Annual base salary', exact: true })).toHaveValue('1452000.00');
    expect(state.requests.filter(request => request.path.endsWith('/salary-changes') && request.method === 'POST')).toHaveLength(1);
    await expect(page.getByRole('button', { name: 'Save salary', exact: true })).toBeDisabled();
    await page.getByRole('button', { name: 'I’ve reviewed this record; keep my draft', exact: true }).click();
    await expect(page.getByRole('button', { name: 'Save salary', exact: true })).toBeEnabled();
    state.salaryConflict = false;
    await page.getByRole('button', { name: 'Save salary', exact: true }).click();
    await expect(page.getByRole('heading', { name: 'Employee details', exact: true })).toBeVisible();
    const secondWrite = state.requests.filter(request => request.path.endsWith('/salary-changes') && request.method === 'POST')[1];
    expect(JSON.parse(secondWrite.body!)).toEqual({ ...JSON.parse(write.body!), version: 4 });
    expect(secondWrite.headers['idempotency-key']).not.toBe(write.headers['idempotency-key']);
  });

  test('directory pagination requests the next page and renders its employee records', async ({ page }) => {
    const state = await fixtureApi(page);
    await open(page, '/employees');
    await expect(page.getByRole('link', { name: 'Priya Sharma', exact: true })).toBeVisible();
    await page.getByRole('button', { name: /Next/, exact: false }).click();
    await expect(page.getByRole('link', { name: 'Sam Rivera', exact: true })).toBeVisible();
    const directory = state.requests.filter(request => request.path === '/api/employees').at(-1)!;
    expect(directory.query.get('page')).toBe('1');
    await expect(page.getByRole('link', { name: 'Priya Sharma', exact: true })).toHaveCount(0);
  });

  test('an uncertain salary save retains its original request while reviewing history and retrying', async ({ page }) => {
    const state = await fixtureApi(page, { salaryUncertain: true, historyWithChange: true });
    await open(page, '/employees/EMP-004218/salary');
    const amount = page.getByRole('textbox', { name: 'Annual base salary', exact: true });
    const reason = page.getByRole('textbox', { name: 'Reason for change', exact: true });
    const effective = page.getByRole('textbox', { name: 'Effective from', exact: true });
    await amount.fill('1452000.00');
    await reason.fill('Annual compensation review');
    await effective.fill('2026-10-15');
    await page.getByRole('button', { name: 'Save salary', exact: true }).click();
    await expect(page.getByText('Your original change is retained. Retry that same change to confirm whether it was saved.', { exact: true })).toBeVisible();
    await expect(amount).toBeDisabled();
    await expect(reason).toBeDisabled();
    await expect(effective).toBeDisabled();
    const firstWrite = state.requests.find(request => request.path.endsWith('/salary-changes') && request.method === 'POST')!;
    await page.getByText('View history', { exact: true }).click();
    await expect(page.getByRole('heading', { name: 'Update salary', exact: true })).toBeVisible();
    await expect(page.getByRole('cell', { name: 'Annual compensation review', exact: true })).toBeVisible();
    await expect(amount).toHaveValue('1452000.00');
    state.salaryUncertain = false;
    await page.getByRole('button', { name: 'Retry original change', exact: true }).click();
    await expect(page.getByRole('heading', { name: 'Employee details', exact: true })).toBeVisible();
    const writes = state.requests.filter(request => request.path.endsWith('/salary-changes') && request.method === 'POST');
    expect(writes).toHaveLength(2);
    expect(writes[1].body).toBe(firstWrite.body);
    expect(writes[1].headers['idempotency-key']).toBe(firstWrite.headers['idempotency-key']);
    await expect(page.getByText('Salary change saved. This record shows the latest employee information.', { exact: true })).toBeVisible();
  });

  test('adding an employee sends the complete creation contract and opens the saved record', async ({ page }) => {
    const state = await fixtureApi(page);
    await open(page, '/employees/new');
    await expect(page.getByRole('heading', { name: 'Add employee', exact: true })).toBeVisible();
    const draft = {
      employeeId: 'EMP-004220', fullName: 'Alex Morgan', workEmail: 'alex.morgan@acme.example',
      countryCode: 'IN', department: 'Engineering', jobTitle: 'Software Engineer', jobLevel: 'L3',
      annualBaseSalary: '1250000.50', currency: 'INR', joiningDate: '2026-09-01', initialSalaryEffectiveFrom: '2026-09-01',
    };
    for (const [label, value] of [
      ['Employee ID', draft.employeeId], ['Full name', draft.fullName], ['Work email', draft.workEmail],
      ['Job title', draft.jobTitle], ['Annual base salary', draft.annualBaseSalary], ['Joining date', draft.joiningDate],
    ]) await page.getByRole('textbox', { name: label, exact: true }).fill(value);
    for (const [label, value] of [['Country', 'India'], ['Department', 'Engineering'], ['Job level', 'L3'], ['Currency', 'INR — Indian rupee']]) {
      await page.getByRole('combobox', { name: label, exact: true }).selectOption({ label: value });
    }
    await expect(page.getByRole('textbox', { name: 'Initial salary effective from', exact: true })).toHaveValue(draft.joiningDate);
    await page.getByRole('button', { name: 'Save employee', exact: true }).click();
    await expect(page.getByText('Employee created successfully.', { exact: true })).toBeVisible();
    await expect(page.getByRole('heading', { name: draft.fullName, exact: true })).toBeVisible();
    const writes = state.requests.filter(request => request.path === '/api/employees' && request.method === 'POST');
    expect(writes).toHaveLength(1);
    expect(JSON.parse(writes[0].body!)).toEqual(draft);
    expect(writes[0].headers['x-csrf-token']).toBe('fixture-authenticated-token');
    await expect(page.getByText('INR 1,250,000.50', { exact: true })).toBeVisible();
  });

  test('editing employee details preserves identity and compensation while sending the reviewed version', async ({ page }) => {
    const state = await fixtureApi(page);
    await open(page, '/employees/EMP-004218/edit');
    await expect(page.getByRole('heading', { name: 'Edit employee', exact: true })).toBeVisible();
    await expect(page.getByRole('textbox', { name: 'Employee ID', exact: true })).toHaveAttribute('readonly', 'readonly');
    await expect(page.getByRole('textbox', { name: 'Annual base salary', exact: true })).toHaveCount(0);
    await expect(page.getByRole('combobox', { name: 'Currency', exact: true })).toHaveCount(0);
    await page.getByRole('textbox', { name: 'Full name', exact: true }).fill('Priya Sharma Rao');
    await page.getByRole('textbox', { name: 'Job title', exact: true }).fill('Staff Software Engineer');
    await page.getByRole('textbox', { name: 'Last employment date', exact: true }).fill('2026-11-12');
    await page.getByRole('button', { name: 'Save changes', exact: true }).click();
    await expect(page.getByText('Employee details saved.', { exact: true })).toBeVisible();
    await expect(page.getByRole('heading', { name: 'Priya Sharma Rao', exact: true })).toBeVisible();
    const write = state.requests.find(request => request.path.endsWith('/details') && request.method === 'PUT')!;
    expect(JSON.parse(write.body!)).toEqual({ fullName: 'Priya Sharma Rao', workEmail: employee.workEmail,
      countryCode: 'IN', department: 'Engineering', jobTitle: 'Staff Software Engineer', jobLevel: 'L4',
      joiningDate: '2026-04-01', lastEmploymentDate: '2026-11-12', version: 3 });
    await expect(page.getByText('INR 1,320,000.00', { exact: true })).toBeVisible();
    await expect(page.getByRole('link', { name: 'Update salary', exact: true })).toBeVisible();
  });

  test('salary history distinguishes the initial baseline, effective dates and recording times', async ({ page }) => {
    const state = await fixtureApi(page, { historyWithChange: true });
    await open(page, '/employees/EMP-004218/history');
    await expect(page.getByRole('heading', { name: 'Salary change history', exact: true })).toBeVisible();
    await expect(page.getByText('INR 1,320,000.00 / year', { exact: true })).toBeVisible();
    await expect(page.getByText('Effective from 01 Apr 2026', { exact: true })).toBeVisible();
    await expect(page.getByText('Recorded 01 Apr 2026, 03:45 UTC', { exact: true })).toBeVisible();
    const row = page.getByRole('row').filter({ has: page.getByRole('cell', { name: 'Annual compensation review', exact: true }) });
    await expect(row).toContainText('15 Oct 2026');
    await expect(row).toContainText('25 Oct 2026, 05:30 UTC');
    await expect(row).toContainText('INR 1,320,000.00');
    await expect(row).toContainText('INR 1,452,000.00');
    await expect(row).toContainText('Maya Rao');
    const request = state.requests.find(request => request.path.endsWith('/salary-changes'))!;
    expect(request.query.get('page')).toBe('0');
    expect(request.query.get('size')).toBe('25');
  });

  test('salary history preserves the initial baseline when there are no changes', async ({ page }) => {
    await fixtureApi(page);
    await open(page, '/employees/EMP-004218/history');
    await expect(page.getByText('No salary changes recorded. The initial salary is preserved above.', { exact: true })).toBeVisible();
    await expect(page.getByText('INR 1,320,000.00 / year', { exact: true })).toBeVisible();
    await expect(page.getByText('Actor: Maya Rao', { exact: true })).toBeVisible();
  });

  test('monthly estimates show server amounts, clear stale results and distinguish errors from zero eligible days', async ({ page }) => {
    const state = await fixtureApi(page, { employeeVersion: 4, records: { [employee.employeeId]: {
      ...employee, annualBaseSalary: '1800000.00', salaryEffectiveFrom: '2026-10-15', lastEmploymentDate: '2026-11-12', version: 4,
    } } });
    await open(page, '/employees/EMP-004218');
    const month = page.getByRole('textbox', { name: 'Selected month', exact: true });
    await month.fill('2026-11');
    await page.getByRole('button', { name: 'Calculate estimate', exact: true }).click();
    await expect(page.getByText('INR 60,000.00', { exact: true })).toBeVisible();
    await expect(page.getByText('12 / 30', { exact: true })).toBeVisible();
    await expect(page.getByText('INR 150,000.00', { exact: true })).toBeVisible();
    expect(state.requests.filter(request => request.path.endsWith('/base-pay-estimate')).at(-1)!.query.get('month')).toBe('2026-11');
    await month.fill('2026-03');
    await expect(page.getByText('INR 60,000.00', { exact: true })).toHaveCount(0);
    state.estimateFailure = true;
    await page.getByRole('button', { name: 'Calculate estimate', exact: true }).click();
    await expect(page.getByText('Salary history is incomplete for this month.', { exact: true })).toBeVisible();
    await expect(month).toHaveValue('2026-03');
    await expect(page.getByText('INR 0.00', { exact: true })).toHaveCount(0);
    state.estimateFailure = false;
    await month.fill('2026-12');
    await page.getByRole('button', { name: 'Calculate estimate', exact: true }).click();
    await expect(page.getByText('INR 0.00', { exact: true })).toBeVisible();
    await expect(page.getByText('0 / 31', { exact: true })).toBeVisible();
    await expect(page.getByText('No employment days fall within this month.', { exact: true })).toBeVisible();
    await expect(page.getByText('Salary history is incomplete for this month.', { exact: true })).toHaveCount(0);
  });

  test('inactivation requires confirmation and preserves access to employee details, history and estimates', async ({ page }) => {
    const state = await fixtureApi(page);
    await open(page, '/employees/EMP-004218');
    await page.getByRole('button', { name: 'Mark inactive', exact: true }).click();
    const dialog = page.getByRole('dialog', { name: 'Confirm employee inactivation', exact: true });
    await expect(dialog).toContainText('Mark Priya Sharma inactive?');
    await dialog.getByRole('button', { name: 'Cancel', exact: true }).click();
    await expect(dialog).toHaveCount(0);
    expect(state.requests.filter(request => request.method === 'PUT')).toHaveLength(0);
    await page.getByRole('button', { name: 'Mark inactive', exact: true }).click();
    await dialog.getByRole('button', { name: 'Mark inactive', exact: true }).click();
    await expect(page.getByText('Employee marked inactive.', { exact: true })).toBeVisible();
    const write = state.requests.find(request => request.path.endsWith('/status') && request.method === 'PUT')!;
    expect(JSON.parse(write.body!)).toEqual({ employmentStatus: 'INACTIVE', version: 3 });
    await expect(page.getByRole('link', { name: 'Update salary', exact: true })).toHaveCount(0);
    await expect(page.getByRole('button', { name: 'Mark inactive', exact: true })).toHaveCount(0);
    await expect(page.getByRole('link', { name: 'View salary history', exact: true })).toBeVisible();
    await expect(page.getByRole('button', { name: 'Calculate estimate', exact: true })).toBeVisible();
    await page.getByRole('link', { name: 'Edit details', exact: true }).click();
    await expect(page.getByRole('heading', { name: 'Edit employee', exact: true })).toBeVisible();
    await expect(page.getByRole('textbox', { name: 'Employment status', exact: true })).toHaveValue('Inactive');
    await expect(page.getByRole('button', { name: 'Save changes', exact: true })).toBeEnabled();
  });

  test('leaving employee details dismisses its inactivation confirmation without changing status', async ({ page }) => {
    const state = await fixtureApi(page);
    await open(page, '/employees');
    await page.getByRole('link', { name: 'Priya Sharma', exact: true }).click();
    await page.getByRole('button', { name: 'Mark inactive', exact: true }).click();
    const dialog = page.getByRole('dialog', { name: 'Confirm employee inactivation', exact: true });
    await expect(dialog).toBeVisible();

    await page.goBack();
    await expect(dialog).toHaveCount(0);
    await expect(page.getByRole('heading', { name: 'Employee directory', exact: true })).toBeVisible();
    expect(state.requests.filter(request => request.path.endsWith('/status') && request.method === 'PUT')).toHaveLength(0);

    await page.goForward();
    await page.getByRole('button', { name: 'Mark inactive', exact: true }).click();
    await dialog.getByRole('button', { name: 'Mark inactive', exact: true }).click();
    await expect(page.getByText('Employee marked inactive.', { exact: true })).toBeVisible();
    expect(state.requests.filter(request => request.path.endsWith('/status') && request.method === 'PUT')).toHaveLength(1);
  });

  test('a pending inactivation disables repeated status submissions', async ({ page }) => {
    await fixtureApi(page);
    let release!: () => void;
    let received!: () => void;
    const pending = new Promise<void>(resolve => { release = resolve; });
    const started = new Promise<void>(resolve => { received = resolve; });
    await page.route('**/api/employees/EMP-004218/status', async route => {
      received();
      await pending;
      await route.fallback();
    });
    await open(page, '/employees/EMP-004218');
    await page.getByRole('button', { name: 'Mark inactive', exact: true }).click();
    await page.getByRole('dialog').getByRole('button', { name: 'Mark inactive', exact: true }).click();
    await started;
    try {
      await expect(page.getByRole('dialog')).toHaveCount(0);
      await expect(page.getByRole('button', { name: 'Mark inactive', exact: true })).toBeDisabled();
    } finally { release(); }
    await expect(page.getByText('Employee marked inactive.', { exact: true })).toBeVisible();
  });

  test('employee forms and conflict review fit a narrow mobile viewport', async ({ page }) => {
    await page.setViewportSize({ width: 390, height: 844 });
    await fixtureApi(page, { salaryConflict: true });
    for (const [path, heading] of [['/employees/EMP-004218', 'Employee details'], ['/employees/new', 'Add employee'], ['/employees/EMP-004218/salary', 'Update salary']]) {
      await open(page, path);
      await expect(page.getByRole('heading', { name: heading, exact: true })).toBeVisible();
      await expect(page.getByText('Loading employee information…', { exact: true })).toHaveCount(0);
      expect(await page.evaluate(() => document.documentElement.scrollWidth)).toBeLessThanOrEqual(390);
    }
    await page.getByRole('textbox', { name: 'Annual base salary', exact: true }).fill('1452000.00');
    await page.getByRole('textbox', { name: 'Reason for change', exact: true }).fill('Annual compensation review');
    await page.getByRole('textbox', { name: 'Effective from', exact: true }).fill('2026-10-15');
    await page.getByRole('button', { name: 'Save salary', exact: true }).click();
    await page.getByRole('button', { name: 'Review latest record', exact: true }).click();
    await expect(page.getByRole('heading', { name: 'Current employee record', exact: true })).toBeVisible();
    expect(await page.evaluate(() => document.documentElement.scrollWidth)).toBeLessThanOrEqual(390);
  });
});
