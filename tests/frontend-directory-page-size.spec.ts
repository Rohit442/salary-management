import { expect, test } from '@playwright/test';
import { fixtureApi, open } from './frontend-fixtures';

test('directory always requests 100 rows without a page-size selector', async ({ page }) => {
  const state = await fixtureApi(page);
  await open(page, '/employees');
  await expect(page.locator('tbody tr')).not.toHaveCount(0);
  expect(state.requests.find(request => request.path === '/api/employees')!.query.get('size')).toBe('100');
  await expect(page.getByLabel(/rows per page/i)).toHaveCount(0);
});
