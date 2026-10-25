import { expect, test } from '@playwright/test';
import { employee, fixtureApi, open } from './frontend-fixtures';

for (const [kind, character] of [['ASCII', 'A'], ['supplementary Unicode', '𐐀']]) {
  test(`salary reason counts ${kind} characters and preserves an overlong draft for correction`, async ({ page }) => {
    const state = await fixtureApi(page);
    await open(page, `/employees/${employee.employeeId}/salary`);
    await expect(page.getByRole('heading', { name: 'Update salary', exact: true })).toBeVisible();
    await page.getByRole('textbox', { name: 'Annual base salary', exact: true }).fill('1500000.00');
    await page.getByRole('textbox', { name: 'Effective from', exact: true }).fill('2026-10-01');
    const reason = page.getByRole('textbox', { name: 'Reason for change', exact: true });
    const error = reason.locator('..').locator('.field-error');
    const boundary = character.repeat(1000);
    await expect(error).toBeEmpty();
    await reason.fill(boundary);
    await expect(reason).toHaveValue(boundary);
    await reason.blur();
    await expect(error).toBeEmpty();
    await reason.fill(`${boundary}${character}`);
    await expect(reason).toHaveValue(`${boundary}${character}`);
    await reason.blur();
    await expect(error).toContainText('1000 characters');
    await page.getByRole('button', { name: 'Save salary', exact: true }).click();
    expect(state.requests.filter(request => request.method === 'POST' && request.path.endsWith('/salary-changes'))).toHaveLength(0);
    await expect(reason).toHaveValue(`${boundary}${character}`);
    await reason.fill(boundary);
    await reason.blur();
    await expect(error).toBeEmpty();
    await page.getByRole('button', { name: 'Save salary', exact: true }).click();
    await expect(page.getByText('Salary change saved. This record shows the latest employee information.', { exact: true })).toBeVisible();
    const writes = state.requests.filter(request => request.method === 'POST' && request.path.endsWith('/salary-changes'));
    expect(writes).toHaveLength(1);
    expect(JSON.parse(writes[0].body!).reason).toBe(boundary);
  });
}
