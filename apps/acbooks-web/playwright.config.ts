import { defineConfig, devices } from "@playwright/test";

const baseURL = "http://127.0.0.1:4174";
const channel = process.env.ACBOOKS_E2E_CHANNEL;
const channelOptions = channel === undefined ? {} : { channel };
const authFile = "e2e/.auth/user.json";

export default defineConfig({
  testDir: "./e2e",
  fullyParallel: false,
  workers: 1,
  forbidOnly: process.env.CI === "true",
  retries: process.env.CI === "true" ? 1 : 0,
  reporter: process.env.CI === "true" ? [["list"], ["html", { open: "never" }]] : "list",
  use: {
    baseURL,
    locale: "zh-CN",
    screenshot: "only-on-failure",
    trace: "retain-on-failure",
  },
  webServer: {
    command: "node e2e/server.mjs",
    url: `${baseURL}/api/v1/health`,
    reuseExistingServer: false,
    timeout: 180_000,
  },
  projects: [
    {
      name: "setup",
      testMatch: /auth\.setup\.ts/,
      use: channelOptions,
    },
    {
      name: "chromium",
      dependencies: ["setup"],
      testIgnore: [/mobile\.spec\.ts/, /auth\.setup\.ts/],
      use: {
        ...devices["Desktop Chrome"],
        storageState: authFile,
        ...channelOptions,
      },
    },
    {
      name: "mobile",
      dependencies: ["setup"],
      testMatch: /mobile\.spec\.ts/,
      use: {
        viewport: { width: 390, height: 844 },
        deviceScaleFactor: 3,
        hasTouch: true,
        isMobile: true,
        locale: "zh-CN",
        storageState: authFile,
        ...channelOptions,
      },
    },
  ],
});
