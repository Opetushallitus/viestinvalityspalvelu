import { defineConfig, devices } from "@playwright/test";

const isCI = !!process.env.CODEBUILD_BUILD_ID || !!process.env.GITHUB_ACTIONS;
const isAgent = !!process.env.CLAUDECODE;
const headless = isCI || isAgent;

export default defineConfig({
  testDir: ".",
  testMatch: "**/*.spec.ts",
  forbidOnly: isCI,
  retries: isCI ? 2 : 0,
  workers: 1,
  timeout: 120_000,
  expect: {
    timeout: 30_000,
  },
  reporter: [
    ["list", { printSteps: true }],
    ["html", { open: headless ? "never" : "always" }],
    ["junit", { outputFile: "playwright-results/junit-playwright.xml" }],
  ],
  use: {
    baseURL: process.env.FRONTEND_HOST ?? "http://localhost:3000",
    headless,
    trace: isCI ? "on-first-retry" : "on",
  },
  outputDir: "playwright-results/",
  projects: [
    {
      name: "chromium",
      use: { ...devices["Desktop Chrome"] },
    },
  ],
});
