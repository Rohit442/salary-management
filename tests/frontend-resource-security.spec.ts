import { expect, test } from '@playwright/test';
import { fixtureApi, open } from './frontend-fixtures';

test.describe('AngularJS resource URL policy', () => {
  test.beforeEach(async ({ page }) => {
    await fixtureApi(page);
    await open(page, '/employees');
    await expect(page.getByRole('heading', { name: 'Employee directory', exact: true })).toBeVisible();
  });

  test('an isolated control reproduces the dependency alternation bug without changing the application policy', async ({ page }) => {
    // Advisory reproduction in a disposable injector, never in the application:
    // https://www.herodevs.com/vulnerability-directory/cve-2026-11998
    // No URL is fetched and no executable payload is created.
    const result = await page.evaluate(() => {
      const angular = (window as any).angular;
      const appSce = angular.element(document).injector().get('$sce');
      const isolated = angular.injector(['ng', ['$sceDelegateProvider', (provider: any) => {
        provider.trustedResourceUrlList([/https:\/\/better\.example\.test\/.*|https:\/\/good\.example\.test\/.*/]);
      }]]);
      const candidate = 'https://evil.example.test/resource-policy-probe.html#https://good.example.test/';
      const trusted = (sce: any, url: string) => {
        try { return sce.getTrustedResourceUrl(url) === url; } catch { return false; }
      };
      try {
        return {
          controlAcceptsIntendedUrl: trusted(isolated.get('$sce'), 'https://good.example.test/resource-policy-probe.html'),
          controlAcceptsCraftedUrl: trusted(isolated.get('$sce'), candidate),
          applicationAcceptsCraftedUrl: trusted(appSce, candidate),
        };
      } finally { isolated.get('$rootScope').$destroy(); }
    });
    expect(result).toEqual({ controlAcceptsIntendedUrl: true, controlAcceptsCraftedUrl: true, applicationAcceptsCraftedUrl: false });
  });

  test('the application rejects every uncached resource URL including same-origin paths', async ({ page }) => {
    const results = await page.evaluate(() => {
      const sce = (window as any).angular.element(document).injector().get('$sce');
      const candidates = [
        '/resource-policy-probe.html',
        `${location.origin}/resource-policy-probe.html`,
        'https://evil.example.test/resource-policy-probe.html#https://good.example.test/',
        'https://evil.example.test/resource-policy-probe.html?next=https://good.example.test/',
        'https://good.example.test.evil.example.test/resource-policy-probe.html',
        '//evil.example.test/resource-policy-probe.html',
        'data:text/html,<p>resource-policy-probe</p>#https://good.example.test/',
        'javascript:void(0)//https://good.example.test/',
      ];
      return candidates.map(url => {
        try { sce.getTrustedResourceUrl(url); return { url, blocked: false, error: '' }; }
        catch (error) { return { url, blocked: true, error: String(error) }; }
      });
    });
    expect(results.filter(result => !result.blocked).map(result => result.url)).toEqual([]);
    for (const result of results) expect(result.error).toContain('$sce:insecurl');
  });

  test('a real ng-include sink cannot fetch or render an unapproved same-origin template', async ({ page }) => {
    const fetched: string[] = [];
    await page.route('**/resource-policy-probe.html', async route => {
      fetched.push(route.request().url());
      await route.fulfill({ status: 200, contentType: 'text/html', body: '<p data-resource-probe-loaded>Unexpected remote template</p>' });
    });
    const result = await page.evaluate(async () => {
      const angular = (window as any).angular;
      const injector = angular.element(document).injector();
      const scope = injector.get('$rootScope').$new();
      scope.resourceUrl = '/resource-policy-probe.html';
      const host = angular.element('<section data-resource-probe-host><div ng-include="resourceUrl"></div></section>');
      document.body.appendChild(host[0]);
      try {
        injector.get('$compile')(host)(scope);
        scope.$apply();
        await new Promise<void>(resolve => injector.get('$browser').notifyWhenNoOutstandingRequests(resolve));
        return { rendered: Boolean(host[0].querySelector('[data-resource-probe-loaded]')) };
      } finally { scope.$destroy(); host.remove(); }
    });
    expect(fetched).toEqual([]);
    expect(result.rendered).toBe(false);
    await expect(page.getByRole('heading', { name: 'Employee directory', exact: true })).toBeVisible();
  });
});
