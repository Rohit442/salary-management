import { expect, test, type Locator } from '@playwright/test';
import { fixtureApi, open } from './frontend-fixtures';

// Include transparent option backgrounds over the white menu surface, so the
// assertion checks the displayed colour without requiring a particular CSS rule.
async function displayedBackground(option: Locator) {
  return option.evaluate(element => {
    const colors: number[][] = [];
    for (let node: Element | null = element; node; node = node.parentElement) {
      colors.push((getComputedStyle(node).backgroundColor.match(/[\d.]+/g) || []).map(Number));
    }
    let result = [255, 255, 255];
    for (const color of colors.reverse()) {
      const alpha = color.length === 4 ? color[3] : color.length === 3 ? 1 : 0;
      result = result.map((channel, index) => Math.round((color[index] || 0) * alpha + channel * (1 - alpha)));
    }
    return result.join(',');
  });
}

const controls = [
  ['Country', 'India', 'United States'],
  ['Department', 'Engineering', 'Finance'],
  ['Job level', 'L4', 'L5'],
  ['Currency', 'Indian rupee', 'US dollar'],
];

test.describe('Multi-select pointer highlights', () => {
  for (const route of ['/employees', '/reports']) {
    const cases = route === '/reports' ? [...controls, ['Group by', 'Country', 'Job level']] : controls;
    for (const [label, selectedName, nextName] of cases) {
      test(`${route} ${label} clears a selected option highlight when the pointer leaves`, async ({ page }, testInfo) => {
        await fixtureApi(page);
        await open(page, route);
        await page.locator(`md-select[aria-label^="${label}"]`).click();
        const selected = page.getByRole('option', { name: selectedName, exact: true });
        const next = page.getByRole('option', { name: nextName, exact: true });
        await selected.click();
        await expect(selected).toHaveAttribute('aria-selected', 'true');
        await next.hover();
        await testInfo.attach('selected-option-state.json', {
          contentType: 'application/json',
          body: JSON.stringify(await selected.evaluate(element => ({
            focused: document.activeElement === element, hovered: element.matches(':hover'),
            classes: element.className, background: getComputedStyle(element).backgroundColor,
          }))),
        });
        await expect.poll(() => displayedBackground(selected)).toBe('255,255,255');
        await expect.poll(() => displayedBackground(next)).not.toBe('255,255,255');
        await page.mouse.move(5, 5);
        await expect.poll(() => displayedBackground(selected)).toBe('255,255,255');
        await expect.poll(() => displayedBackground(next)).toBe('255,255,255');
        await expect(selected).toHaveAttribute('aria-selected', 'true');
        const checkmark = await selected.locator('.md-icon').evaluate(element => {
          const style = getComputedStyle(element, '::after');
          return { display: style.display, borderStyle: style.borderStyle, width: parseFloat(style.width), height: parseFloat(style.height) };
        });
        expect(checkmark.display).not.toBe('none');
        expect(checkmark.borderStyle).toContain('solid');
        expect(checkmark.width).toBeGreaterThan(0);
        expect(checkmark.height).toBeGreaterThan(0);
      });
    }
  }

  test('keyboard focus stays visible without a sticky grey option background', async ({ page }) => {
    await fixtureApi(page);
    await open(page, '/employees');
    const control = page.locator('md-select[aria-label="Country"]');
    await control.click();
    const listbox = page.getByRole('listbox', { name: 'Country', exact: true });
    await expect(listbox).toBeFocused();
    await page.mouse.move(5, 5);
    await page.keyboard.press('ArrowDown');
    await expect(listbox).toHaveAttribute('aria-activedescendant', /.+/);
    const activeId = await listbox.getAttribute('aria-activedescendant');
    const focused = listbox.locator('md-option.md-focused');
    await expect(focused).toHaveCount(1);
    await expect(focused).toHaveAttribute('id', activeId!);
    await expect.poll(() => displayedBackground(focused)).toBe('255,255,255');
    const focusStyle = await focused.evaluate(element => {
      const style = getComputedStyle(element);
      return { outline: style.outlineStyle !== 'none' && parseFloat(style.outlineWidth) > 0, shadow: style.boxShadow !== 'none' };
    });
    expect(focusStyle.outline || focusStyle.shadow).toBe(true);
  });
});
