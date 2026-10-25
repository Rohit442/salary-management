import { expect, test } from '@playwright/test';
import { fixtureApi, open } from './frontend-fixtures';

// Committed Chromium baselines use macOS, matching the visual CI runner.
// Other browsers retain structural rendering checks and review screenshots.
const screens = [
  ['sign-in', '/sign-in', 'Sign in to ACME'],
  ['directory', '/employees', 'Employee directory'],
  ['details', '/employees/EMP-004218', 'Employee details'],
  ['history', '/employees/EMP-004218/history', 'Salary change history'],
  ['salary', '/employees/EMP-004218/salary', 'Update salary'],
  ['add', '/employees/new', 'Add employee'],
  ['edit', '/employees/EMP-004218/edit', 'Edit employee'],
  ['reports', '/reports', 'Compensation reports'],
];

for (const width of [1440, 390]) {
  for (const [name, path, heading] of screens) {
    test(`${name} renders at ${width}px`, async ({ page }, testInfo) => {
      const errors: string[] = [];
      page.on('pageerror', error => errors.push(error.message));
      await page.setViewportSize({ width, height: width === 1440 ? 1000 : 844 });
      await fixtureApi(page, { signedIn: name !== 'sign-in' });
      await open(page, path);
      await expect(page.getByRole('heading', { name: heading, exact: true })).toBeVisible();
      await expect(page.getByRole('status').filter({ hasText: 'Loading' })).toHaveCount(0);
      await page.evaluate(() => document.fonts.ready);
      const dimensions = await page.evaluate(() => ({ viewport: window.innerWidth, width: document.documentElement.scrollWidth,
        overflowing: [...document.querySelectorAll('body *')].filter(element => element.getBoundingClientRect().right > window.innerWidth && !element.closest('.table-wrap'))
          .map(element => `${element.tagName}.${element.className}`).slice(0, 12) }));
      expect(dimensions.width, JSON.stringify(dimensions)).toBeLessThanOrEqual(dimensions.viewport);
      expect(errors).toEqual([]);
      if (testInfo.project.name === 'chromium') {
        await expect(page).toHaveScreenshot(`${name}-${width}.png`, {
          animations: 'disabled', caret: 'hide', fullPage: true, maxDiffPixelRatio: 0.01,
        });
      }
      await page.screenshot({ path: testInfo.outputPath(`${name}-${width}.png`), fullPage: true });
    });
  }
}
