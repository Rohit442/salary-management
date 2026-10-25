import { test, expect } from '@playwright/test';

// Verifies browser installation only; replace with application workflows once built.
test('browser can render a page and respond to input', async ({ page }) => {
  await page.setContent(`
    <html lang="en">
      <head><title>Playwright setup check</title></head>
      <body>
        <h1>Playwright setup check</h1>
        <label for="employee">Employee search</label>
        <input id="employee" type="search" />
      </body>
    </html>
  `);

  await expect(page.getByRole('heading', { name: 'Playwright setup check' })).toBeVisible();
  await page.getByRole('searchbox', { name: 'Employee search' }).fill('ACME-001');
  await expect(page.getByRole('searchbox', { name: 'Employee search' })).toHaveValue('ACME-001');
});
