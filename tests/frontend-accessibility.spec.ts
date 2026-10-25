import { expect, test } from '@playwright/test';
import { fixtureApi, open } from './frontend-fixtures';

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

test('ACME brand returns to Employees by keyboard and click', async ({ page, browserName }) => {
  await page.goto(`${frontendUrl}/#!/reports`);
  await expect(page.getByRole('heading', { name: 'Compensation reports' })).toBeVisible();
  const brand = page.getByRole('link', { name: 'ACME', exact: true });
  await expect(brand).toBeVisible();
  await expect(brand).toHaveAttribute('href', '#!/employees');
  await page.getByRole('link', { name: 'Skip to content' }).focus();
  // macOS WebKit uses Option+Tab to include links in keyboard navigation.
  await page.keyboard.press(browserName === 'webkit' && process.platform === 'darwin' ? 'Alt+Tab' : 'Tab');
  await expect(brand).toBeFocused();
  await page.keyboard.press('Enter');
  await expect(page.getByRole('heading', { name: 'Employee directory', exact: true })).toBeVisible();
  await expect(page).toHaveURL(/#!\/employees$/);
  await page.getByRole('link', { name: 'Reports', exact: true }).click();
  await expect(page.getByRole('heading', { name: 'Compensation reports' })).toBeVisible();
  await brand.click();
  await expect(page.getByRole('heading', { name: 'Employee directory', exact: true })).toBeVisible();
  await expect(page).toHaveURL(/#!\/employees$/);
});

test('mobile ACME brand returns to Employees and closes navigation', async ({ page }) => {
  await page.setViewportSize({ width: 390, height: 844 });
  await page.goto(`${frontendUrl}/#!/reports`);
  await expect(page.getByRole('heading', { name: 'Compensation reports' })).toBeVisible();
  const menu = page.getByRole('button', { name: 'Toggle navigation' });
  await menu.click();
  await expect(menu).toHaveAttribute('aria-expanded', 'true');
  const brand = page.getByRole('link', { name: 'ACME', exact: true });
  await expect(brand).toBeVisible();
  await brand.click();
  await expect(page.getByRole('heading', { name: 'Employee directory', exact: true })).toBeVisible();
  await expect(page).toHaveURL(/#!\/employees$/);
  await expect(menu).toHaveAttribute('aria-expanded', 'false');
  await expect(page.locator('.sidebar-scrim')).toHaveCount(0);
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

test.describe('Mobile sign-out reachability', () => {
  test.use({ hasTouch: true });

  for (const viewport of [{ width: 390, height: 400 }, { width: 568, height: 320 }]) {
    test(`Sign out stays visible without scrolling at ${viewport.width}x${viewport.height}`, async ({ page }, testInfo) => {
      await page.setViewportSize(viewport);
      const state = await fixtureApi(page);
      await open(page, '/reports');
      await expect(page.getByRole('heading', { name: 'Compensation reports', exact: true })).toBeVisible();
      await page.getByRole('button', { name: 'Toggle navigation' }).tap();
      const sidebar = page.getByRole('complementary', { name: 'Workspace navigation' });
      const signout = sidebar.getByRole('button', { name: 'Sign out', exact: true });
      await expect(sidebar).toBeInViewport({ ratio: 1 });
      // Assert before tapping: Playwright would otherwise auto-scroll a clipped control.
      await expect.poll(async () => {
        const box = await signout.boundingBox();
        return box && box.y >= 0 && box.y + box.height <= viewport.height;
      }).toBe(true);
      const beforeScroll = await signout.boundingBox();
      await page.mouse.move(120, 100);
      await page.mouse.wheel(0, 600);
      await expect(sidebar.getByRole('link', { name: 'Reports', exact: true })).toBeInViewport({ ratio: 1 });
      expect(await signout.boundingBox()).toEqual(beforeScroll);
      await page.screenshot({ path: testInfo.outputPath('mobile-signout.png') });
      await signout.tap();
      await expect(page.getByRole('heading', { name: 'Sign in to ACME', exact: true })).toBeVisible();
      expect(state.requests.filter(request => request.path === '/api/auth/logout')).toHaveLength(1);
    });
  }

  test('open menu adapts when the mobile viewport gets shorter', async ({ page }) => {
    await page.setViewportSize({ width: 390, height: 844 });
    await fixtureApi(page);
    await open(page, '/reports');
    await page.getByRole('button', { name: 'Toggle navigation' }).tap();
    await page.setViewportSize({ width: 390, height: 550 });
    const signout = page.getByRole('button', { name: 'Sign out', exact: true });
    await expect(signout).toBeInViewport({ ratio: 1 });
    await signout.tap();
    await expect(page.getByRole('heading', { name: 'Sign in to ACME', exact: true })).toBeVisible();
  });
});
