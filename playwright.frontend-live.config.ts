import { defineConfig, devices } from '@playwright/test';

export default defineConfig({
  testDir: './tests', testMatch: ['**/frontend-live.spec.ts', '**/frontend-validation-live.spec.ts'],
  fullyParallel: false, workers: 1, retries: 0,
  outputDir: 'test-results/live', reporter: 'list',
  use: { baseURL: 'http://127.0.0.1:5174', screenshot: 'only-on-failure', trace: 'retain-on-failure' },
  projects: [{ name: 'chromium', use: { ...devices['Desktop Chrome'] } }],
  webServer: [
    {
      command: 'java -jar backend/target/salary-management-0.0.1-SNAPSHOT.jar --server.address=127.0.0.1 --server.port=18080 "--spring.datasource.url=jdbc:h2:mem:frontend-browser;DB_CLOSE_DELAY=-1" --spring.liquibase.contexts=demo --spring.liquibase.parameters.employeeCount=20 --spring.h2.console.enabled=false',
      url: 'http://127.0.0.1:18080/api/auth/csrf', timeout: 120_000, reuseExistingServer: false,
    },
    {
      command: 'npm run dev -- --port 5174',
      env: { ACME_API_URL: 'http://127.0.0.1:18080' },
      url: 'http://127.0.0.1:5174', timeout: 30_000, reuseExistingServer: false,
    },
  ],
});
