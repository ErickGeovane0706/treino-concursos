import { defineConfig, devices } from '@playwright/test';

/**
 * Testes de ponta a ponta contra o site rodando (docker-compose.e2e.yml).
 * RF12/#12: distancia dos anuncios medida em 360 px (celular) e 1920 px (computador).
 */
export default defineConfig({
  testDir: './e2e',
  globalSetup: './e2e/preparar-dados.ts',
  timeout: 30_000,
  retries: process.env.CI ? 1 : 0,
  reporter: process.env.CI ? [['list'], ['html', { open: 'never' }]] : 'list',
  use: {
    baseURL: process.env.E2E_BASE_URL ?? 'http://127.0.0.1:18081',
    trace: 'retain-on-failure',
    screenshot: 'only-on-failure',
  },
  projects: [
    {
      name: 'celular-360',
      use: { ...devices['Pixel 5'], viewport: { width: 360, height: 740 } },
    },
    {
      name: 'computador-1920',
      use: { ...devices['Desktop Chrome'], viewport: { width: 1920, height: 1080 } },
    },
  ],
});
