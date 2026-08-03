import { defineConfig, devices } from "@playwright/test";

/**
 * project-docs/prompts/23-testing.md: "high-value E2E ... Playwright." Scoped to
 * flows this app can actually drive end-to-end today — see e2e/README.md for the
 * flows that are deliberately NOT here (host publish, admin moderate, payment
 * confirm) and why.
 *
 * Needs a live backend (real Postgres/Redis via `infra/docker-compose.yml`) and the
 * web app both running — see the `webServer` block below. `E2E_BASE_URL` lets CI
 * point this at an already-running stack instead of spawning one.
 */
export default defineConfig({
  testDir: "./e2e",
  globalSetup: "./e2e/seed.ts",
  fullyParallel: true,
  forbidOnly: Boolean(process.env.CI),
  retries: process.env.CI ? 1 : 0,
  reporter: process.env.CI ? [["github"], ["html", { open: "never" }]] : "list",
  use: {
    baseURL: process.env.E2E_BASE_URL ?? "http://localhost:3000",
    trace: "on-first-retry",
  },
  projects: [
    { name: "chromium", use: { ...devices["Desktop Chrome"] } },
    // Closes prompt 22's own deferred gap: "E2E on mobile viewport (Playwright)."
    { name: "mobile-chromium", use: { ...devices["Pixel 7"] } },
  ],
  webServer: process.env.E2E_BASE_URL
    ? undefined
    : {
        command: "npm run start",
        url: "http://localhost:3000",
        reuseExistingServer: !process.env.CI,
        timeout: 120_000,
      },
});
