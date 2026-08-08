import { defineConfig } from "@playwright/test";

export default defineConfig({
  testDir: "./tests",
  timeout: 240_000,
  reporter: [["list"], ["html", { outputFolder: "playwright-report" }]],
  workers: 1
});
