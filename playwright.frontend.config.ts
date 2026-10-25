import { defineConfig } from '@playwright/test';
import base from './playwright.config';

export default defineConfig(base, {
  testMatch: ['**/frontend.spec.ts', '**/frontend-directory*.spec.ts', '**/frontend-filter-hover.spec.ts', '**/frontend-accessibility.spec.ts', '**/frontend-visual.spec.ts', '**/frontend-resource-security.spec.ts', '**/frontend-salary*.spec.ts'],
});
