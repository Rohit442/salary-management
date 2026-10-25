import { expect, test } from '@playwright/test';

const frontendUrl = process.env.FRONTEND_URL || 'http://127.0.0.1:5173';

test.beforeEach(async ({ page }) => {
  await page.route('**/api/**', async route => {
    const path = new URL(route.request().url()).pathname;
    const body = path.endsWith('/me')
      ? { user: { id: 'hr-fixture', email: 'hr@acme.example', displayName: 'Maya Rao', roles: ['HR'] }, idleTimeoutSeconds: 1800 }
      : path.endsWith('/reference-data') ? { countries: [], departments: [], jobLevels: [], currencies: [] }
      : path.endsWith('/compensation') ? { groupBy: ['department', 'currency'], currencySummaries: [], groups: [], totalEmployees: 0 }
      : { items: [], page: 0, size: 25, totalElements: 0, totalPages: 0, hasPrevious: false, hasNext: false };
    await route.fulfill({ status: 200, json: body });
  });
});

test('fresh sign-in has a sign-in title and no session-loss notice', async ({ page }) => {
  await page.route('**/api/auth/me', route => route.fulfill({ status: 401, json: {
    code: 'AUTHENTICATION_REQUIRED', message: 'Please sign in to access the HR workspace.', fieldErrors: [],
  } }));
  await page.goto(`${frontendUrl}/#!/sign-in`);
  await expect(page.getByRole('heading', { name: 'Sign in to ACME', exact: true })).toBeVisible();
  await expect(page.getByText('Please sign in to access the HR workspace.', { exact: true })).toHaveCount(0);
  await expect(page).toHaveTitle('ACME · Sign in');
});

test('keyboard skip link focuses main content without changing the page', async ({ page }) => {
  await page.goto(`${frontendUrl}/#!/reports`);
  await expect(page.getByRole('heading', { name: 'Compensation reports' })).toBeVisible();
  await page.getByRole('link', { name: 'Skip to content' }).focus();
  await page.keyboard.press('Enter');
  await expect(page.getByRole('main')).toBeFocused();
  await expect(page).toHaveURL(/#!\/reports$/);
});

test('mobile navigation opens and closes when switching pages', async ({ page }) => {
  await page.setViewportSize({ width: 390, height: 844 });
  await page.goto(`${frontendUrl}/#!/employees`);
  const menu = page.getByRole('button', { name: 'Toggle navigation' });
  await expect(menu).toHaveAttribute('aria-expanded', 'false');
  await menu.click();
  await expect(menu).toHaveAttribute('aria-expanded', 'true');
  await page.getByRole('link', { name: 'Reports', exact: true }).click();
  await expect(page.getByRole('heading', { name: 'Compensation reports' })).toBeVisible();
  await expect(menu).toHaveAttribute('aria-expanded', 'false');
  expect(await page.evaluate(() => document.documentElement.scrollWidth <= window.innerWidth)).toBe(true);
});
