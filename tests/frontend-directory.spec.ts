import { expect, test, type Page } from '@playwright/test';
import { employee, fixtureApi, open, type FixtureState } from './frontend-fixtures';

const search = (page: Page) => page.getByLabel('Search by name or employee ID', { exact: true });
const suggestions = (page: Page) => page.getByRole('listbox', { name: 'Employee suggestions', exact: true });
const filterControl = (page: Page, label: string) => page.locator(`md-select[aria-label^="${label}"]`);
const directoryRequests = (state: FixtureState) => state.requests.filter(request => request.path === '/api/employees' && request.method === 'GET');
const suggestionRequests = (state: FixtureState) => state.requests.filter(request => request.path === '/api/employees/suggestions');
const lastDirectory = (state: FixtureState) => directoryRequests(state).at(-1)!;
const person = (fullName: string, index: number) => ({ ...employee, employeeId: `EMP-${String(6000 + index).padStart(6, '0')}`, fullName, workEmail: `fixture.${index}@acme.example` });
const names = [person('Alice Martin', 1), person('Alina Bose', 2), person('Mallory Alcott', 3), person('Zara Stone', 4), person('Al% Literal', 5), person('Al_ Literal', 6)];
const manyPeople = () => Array.from({ length: 225 }, (_, index) => person(`Employee ${String(index + 1).padStart(3, '0')}`, index + 10));
const leaveField = (page: Page) => page.getByRole('heading', { name: 'Employee directory', exact: true }).click();

async function loadDirectory(page: Page, initial: Partial<FixtureState> = {}) {
  const state = await fixtureApi(page, { directoryRecords: names, ...initial });
  await open(page, '/employees');
  await expect(page.getByRole('table')).toBeVisible();
  return state;
}

test.describe('Employee directory interactions', () => {
  test.use({ viewport: { width: 1440, height: 1000 } });

  test('suggestions start at one character, debounce for 300ms and never replace the table while typing', async ({ page }, testInfo) => {
    await page.clock.install();
    const state = await loadDirectory(page);
    await page.clock.pauseAt(new Date(Date.now() + 1000));
    await search(page).fill('a');
    await page.clock.runFor(299);
    expect(suggestionRequests(state)).toHaveLength(0);
    const firstResponse = page.waitForResponse(response => new URL(response.url()).pathname === '/api/employees/suggestions');
    await page.clock.runFor(1);
    await expect.poll(() => suggestionRequests(state).length).toBe(1);
    await (await firstResponse).finished();
    await page.clock.runFor(20);
    await expect(suggestions(page).getByRole('option', { name: 'Alice Martin', exact: true })).toBeVisible();
    expect(suggestionRequests(state)[0].query.get('prefix')).toBe('a');
    expect(suggestionRequests(state)[0].query.get('limit')).toBe('8');
    await search(page).fill('Al');
    await page.clock.runFor(200);
    await search(page).fill('Ali');
    await page.clock.runFor(299);
    expect(suggestionRequests(state)).toHaveLength(1);
    const nextResponse = page.waitForResponse(response => new URL(response.url()).pathname === '/api/employees/suggestions');
    await page.clock.runFor(1);
    await expect.poll(() => suggestionRequests(state).length).toBe(2);
    await (await nextResponse).finished();
    await page.clock.runFor(20);
    await expect(suggestions(page).getByRole('option')).toHaveText(['Alice Martin', 'Alina Bose']);
    expect(suggestionRequests(state)[1].query.get('prefix')).toBe('Ali');
    expect(directoryRequests(state)).toHaveLength(1);
    await expect(page.getByRole('table').getByRole('link', { name: 'Zara Stone', exact: true })).toBeVisible();
    await expect(suggestions(page)).not.toContainText('EMP-');
    expect((await suggestions(page).textContent())?.replace(/\s/g, '')).toBe('AliceMartinAlinaBose');
    await page.screenshot({ path: testInfo.outputPath('employee-suggestions.png') });
  });

  test('a suggestion click opens that employee without applying the typed table query', async ({ page }) => {
    const state = await loadDirectory(page);
    await search(page).fill('ali');
    await suggestions(page).getByRole('option', { name: 'Alina Bose', exact: true }).click();
    await expect(page.getByRole('heading', { name: 'Employee details', exact: true })).toBeVisible();
    await expect(page.getByRole('heading', { name: 'Alina Bose', exact: true })).toBeVisible();
    expect(directoryRequests(state)).toHaveLength(1);
    expect(state.requests.some(request => request.path === '/api/employees/EMP-006002')).toBe(true);
  });

  test('keyboard arrows select names, Escape dismisses suggestions and Enter opens the active employee', async ({ page }) => {
    const state = await loadDirectory(page);
    await search(page).fill('Ali');
    await expect(suggestions(page).getByRole('option')).toHaveText(['Alice Martin', 'Alina Bose']);
    await search(page).press('ArrowDown');
    await expect(suggestions(page).getByRole('option', { name: 'Alice Martin', exact: true })).toHaveAttribute('aria-selected', 'true');
    await search(page).press('ArrowDown');
    await expect(suggestions(page).getByRole('option', { name: 'Alina Bose', exact: true })).toHaveAttribute('aria-selected', 'true');
    await search(page).press('ArrowUp');
    await expect(suggestions(page).getByRole('option', { name: 'Alice Martin', exact: true })).toHaveAttribute('aria-selected', 'true');
    await search(page).press('Escape');
    await expect(suggestions(page)).toBeHidden();
    expect(directoryRequests(state)).toHaveLength(1);
    await search(page).fill('Alic');
    await expect(suggestions(page).getByRole('option', { name: 'Alice Martin', exact: true })).toBeVisible();
    await search(page).press('ArrowDown');
    await search(page).press('Enter');
    await expect(page.getByRole('heading', { name: 'Alice Martin', exact: true })).toBeVisible();
    expect(directoryRequests(state)).toHaveLength(1);
  });

  test('a late suggestion response cannot overwrite a newer prefix result', async ({ page }) => {
    let release!: () => void;
    const gate = new Promise<void>(resolve => { release = resolve; });
    const state = await loadDirectory(page, { suggestionResponseGate: prefix => prefix === 'Al' ? gate : Promise.resolve() });
    await search(page).fill('Al');
    await expect.poll(() => suggestionRequests(state).length).toBe(1);
    await search(page).fill('Z');
    await expect(suggestions(page).getByRole('option')).toHaveText(['Zara Stone']);
    const oldResponse = page.waitForResponse(response => new URL(response.url()).pathname === '/api/employees/suggestions' && new URL(response.url()).searchParams.get('prefix') === 'Al');
    release();
    await (await oldResponse).finished();
    await page.evaluate(async () => {
      await new Promise<void>(resolve => requestAnimationFrame(() => requestAnimationFrame(() => resolve())));
    });
    await expect(suggestions(page).getByRole('option')).toHaveText(['Zara Stone']);
    expect(directoryRequests(state)).toHaveLength(1);
  });

  test('suggestion failure leaves the table usable and search blur applies a substring query', async ({ page }) => {
    const state = await loadDirectory(page, { suggestionsFailure: true });
    await search(page).fill('lic');
    await expect.poll(() => suggestionRequests(state).length).toBe(1);
    await expect(page.getByRole('table').getByRole('link', { name: 'Zara Stone', exact: true })).toBeVisible();
    await leaveField(page);
    await expect(page.getByRole('table').getByRole('link', { name: 'Alice Martin', exact: true })).toBeVisible();
    await expect(page.getByRole('table').getByRole('link', { name: 'Zara Stone', exact: true })).toHaveCount(0);
    expect(lastDirectory(state).query.get('q')).toBe('lic');
    expect(lastDirectory(state).query.get('page')).toBe('0');
    await expect(page.getByRole('alert')).toHaveCount(0);
  });

  test('prefix punctuation is sent literally and the dropdown contains only returned names', async ({ page }) => {
    const state = await loadDirectory(page);
    await search(page).fill('al%');
    await expect(suggestions(page).getByRole('option')).toHaveText(['Al% Literal']);
    expect(suggestionRequests(state).at(-1)!.query.get('prefix')).toBe('al%');
    await search(page).fill('Al_');
    await expect(suggestions(page).getByRole('option')).toHaveText(['Al_ Literal']);
    expect(suggestionRequests(state).at(-1)!.query.get('prefix')).toBe('Al_');
    expect(directoryRequests(state)).toHaveLength(1);
  });

  test('each multi-select applies only after focus leaves its control and popup', async ({ page }) => {
    const state = await loadDirectory(page);
    for (const [label, option, key, value] of [
      ['Country', 'India', 'country', 'IN'], ['Department', 'Engineering', 'department', 'Engineering'],
      ['Job level', 'L4', 'jobLevel', 'L4'], ['Currency', 'Indian rupee', 'currency', 'INR'],
    ]) {
      const before = directoryRequests(state).length;
      await page.getByRole('button', { name: label, exact: true }).click();
      await page.getByRole('option', { name: option, exact: true }).click();
      expect(directoryRequests(state)).toHaveLength(before);
      await page.keyboard.press('Escape');
      await expect(filterControl(page, label)).toBeFocused();
      expect(directoryRequests(state)).toHaveLength(before);
      await leaveField(page);
      await expect.poll(() => directoryRequests(state).length).toBe(before + 1);
      expect(lastDirectory(state).query.getAll(key)).toEqual([value]);
      expect(lastDirectory(state).query.get('page')).toBe('0');
    }
    await expect(page.getByRole('button', { name: 'Apply filters', exact: true })).toHaveCount(0);
  });

  test('Clear filters resets query, selections and page immediately while retaining the selected sort', async ({ page }) => {
    const state = await loadDirectory(page, { directoryRecords: manyPeople() });
    await search(page).fill('Employee');
    await leaveField(page);
    await page.getByRole('button', { name: 'Country', exact: true }).click();
    await page.getByRole('option', { name: 'India', exact: true }).click();
    await page.keyboard.press('Escape');
    await expect(filterControl(page, 'Country')).toBeFocused();
    await leaveField(page);
    await expect.poll(() => lastDirectory(state).query.getAll('country')).toEqual(['IN']);
    await page.getByRole('table').getByRole('button', { name: /^Employee/ }).click();
    await expect.poll(() => lastDirectory(state).query.get('sort')).toBe('fullName,desc');
    await page.getByLabel('Page number', { exact: true }).fill('2');
    await page.getByRole('button', { name: 'Go', exact: true }).click();
    await expect.poll(() => lastDirectory(state).query.get('page')).toBe('1');
    await page.getByRole('button', { name: 'Clear filters', exact: true }).click();
    await expect(search(page)).toHaveValue('');
    await expect.poll(() => lastDirectory(state).query.get('q')).toBeNull();
    expect(lastDirectory(state).query.getAll('country')).toEqual([]);
    expect(lastDirectory(state).query.get('page')).toBe('0');
    expect(lastDirectory(state).query.get('sort')).toBe('fullName,desc');
    await expect(page.getByLabel('Page number', { exact: true })).toHaveValue('1');
  });

  test('pagination uses 100 rows, accepts Go and Enter, and blocks invalid page numbers', async ({ page }) => {
    const state = await loadDirectory(page, { directoryRecords: manyPeople() });
    expect(lastDirectory(state).query.get('size')).toBe('100');
    await expect(page.getByRole('table').locator('tbody tr')).toHaveCount(100);
    const number = page.getByLabel('Page number', { exact: true });
    await expect(number).toHaveValue('1');
    for (const invalid of ['0', '4', '1.5']) {
      const before = directoryRequests(state).length;
      await number.fill(invalid);
      await page.getByRole('button', { name: 'Go', exact: true }).click();
      expect(directoryRequests(state)).toHaveLength(before);
      await expect(page.getByRole('table').getByRole('link', { name: 'Employee 001', exact: true })).toBeVisible();
    }
    await number.fill('3');
    await page.getByRole('button', { name: 'Go', exact: true }).click();
    await expect(page.getByRole('table').locator('tbody tr')).toHaveCount(25);
    expect(lastDirectory(state).query.get('page')).toBe('2');
    await expect(page.getByRole('button', { name: 'Next', exact: true })).toBeDisabled();
    await number.fill('2');
    await number.press('Enter');
    await expect(page.getByRole('table').getByRole('link', { name: 'Employee 101', exact: true })).toBeVisible();
    expect(lastDirectory(state).query.get('page')).toBe('1');
    await page.getByRole('button', { name: 'Previous', exact: true }).click();
    await expect(number).toHaveValue('1');
    await expect(page.getByRole('button', { name: 'Previous', exact: true })).toBeDisabled();
    await page.getByRole('button', { name: 'Next', exact: true }).click();
    await expect(number).toHaveValue('2');
    expect(directoryRequests(state).every(request => request.query.get('size') === '100')).toBe(true);
  });

  test('returning from an employee profile restores query, filters, sort, page and scroll', async ({ page }) => {
    const state = await loadDirectory(page, { directoryRecords: manyPeople() });
    await search(page).fill('Employee');
    await leaveField(page);
    await page.getByRole('button', { name: 'Country', exact: true }).click();
    await page.getByRole('option', { name: 'India', exact: true }).click();
    await page.keyboard.press('Escape');
    await expect(filterControl(page, 'Country')).toBeFocused();
    await leaveField(page);
    await expect.poll(() => lastDirectory(state).query.getAll('country')).toEqual(['IN']);
    await page.getByRole('table').getByRole('button', { name: /^Employee/ }).click();
    await expect.poll(() => lastDirectory(state).query.get('sort')).toBe('fullName,desc');
    await page.getByLabel('Page number', { exact: true }).fill('2');
    await page.getByRole('button', { name: 'Go', exact: true }).click();
    await expect(page.getByRole('table').getByRole('link', { name: 'Employee 125', exact: true })).toBeVisible();
    await page.evaluate(() => window.scrollTo(0, 800));
    await expect.poll(() => page.evaluate(() => window.scrollY)).toBe(800);
    const link = page.getByRole('table').getByRole('link', { name: 'Employee 120', exact: true });
    await link.click();
    await expect(page.getByRole('heading', { name: 'Employee 120', exact: true })).toBeVisible();
    await page.getByRole('link', { name: 'Employee directory', exact: true }).click();
    await expect(search(page)).toHaveValue('Employee');
    await expect(page.getByLabel('Page number', { exact: true })).toHaveValue('2');
    await expect(filterControl(page, 'Country')).toContainText('India');
    await expect(page.getByRole('table').getByRole('link', { name: 'Employee 125', exact: true })).toBeVisible();
    expect(lastDirectory(state).query.get('sort')).toBe('fullName,desc');
    expect(lastDirectory(state).query.get('q')).toBe('Employee');
    expect(lastDirectory(state).query.getAll('country')).toEqual(['IN']);
    await expect.poll(() => page.evaluate(() => window.scrollY)).toBe(800);
  });

  test('browser Back restores a suggestion draft without turning it into a table query', async ({ page }) => {
    const state = await loadDirectory(page);
    await search(page).fill('Ali');
    await suggestions(page).getByRole('option', { name: 'Alice Martin', exact: true }).click();
    await expect(page.getByRole('heading', { name: 'Alice Martin', exact: true })).toBeVisible();
    await page.goBack();
    await expect(search(page)).toHaveValue('Ali');
    await expect(page.getByRole('table').getByRole('link', { name: 'Zara Stone', exact: true })).toBeVisible();
    expect(directoryRequests(state).every(request => !request.query.has('q'))).toBe(true);
    await expect(suggestions(page)).toBeHidden();
  });

  test('returning to a smaller result set clamps the remembered page to the last available page', async ({ page }) => {
    const state = await loadDirectory(page, { directoryRecords: manyPeople() });
    await page.getByLabel('Page number', { exact: true }).fill('3');
    await page.getByRole('button', { name: 'Go', exact: true }).click();
    await page.getByRole('table').getByRole('link', { name: 'Employee 201', exact: true }).click();
    await expect(page.getByRole('heading', { name: 'Employee 201', exact: true })).toBeVisible();
    state.directoryRecords = state.directoryRecords!.slice(0, 150);
    await page.getByRole('link', { name: 'Employee directory', exact: true }).click();
    await expect(page.getByLabel('Page number', { exact: true })).toHaveValue('2');
    await expect(page.getByRole('table').locator('tbody tr')).toHaveCount(50);
    await expect(page.getByRole('table').getByRole('link', { name: 'Employee 101', exact: true })).toBeVisible();
    expect(lastDirectory(state).query.get('page')).toBe('1');
    await expect(page.getByRole('button', { name: 'Next', exact: true })).toBeDisabled();
  });

  test('Clear filters cancels an in-flight suggestion and never submits the abandoned draft', async ({ page }) => {
    let release!: () => void;
    const gate = new Promise<void>(resolve => { release = resolve; });
    const state = await loadDirectory(page, { suggestionResponseGate: () => gate });
    await search(page).fill('Ali');
    await expect.poll(() => suggestionRequests(state).length).toBe(1);
    await page.getByRole('button', { name: 'Clear filters', exact: true }).click();
    await expect(search(page)).toHaveValue('');
    await expect.poll(() => directoryRequests(state).length).toBe(2);
    const response = page.waitForResponse(reply => new URL(reply.url()).pathname === '/api/employees/suggestions');
    release();
    await (await response).finished();
    await page.evaluate(async () => new Promise<void>(resolve => requestAnimationFrame(() => requestAnimationFrame(() => resolve()))));
    await expect(suggestions(page)).toBeHidden();
    expect(directoryRequests(state).every(request => !request.query.has('q'))).toBe(true);
    await expect(page.getByRole('table').getByRole('link', { name: 'Zara Stone', exact: true })).toBeVisible();
  });

  test('clicking outside an open filter applies its selected values when the popup closes', async ({ page }) => {
    const state = await loadDirectory(page);
    await filterControl(page, 'Country').click();
    await page.getByRole('option', { name: 'India', exact: true }).click();
    expect(directoryRequests(state)).toHaveLength(1);
    const heading = await page.getByRole('heading', { name: 'Employee directory', exact: true }).boundingBox();
    await page.mouse.click(heading!.x + 10, heading!.y + 10);
    await expect(page.getByRole('option', { name: 'India', exact: true })).toBeHidden();
    await expect.poll(() => lastDirectory(state).query.getAll('country')).toEqual(['IN']);
    expect(directoryRequests(state)).toHaveLength(2);
  });
});
