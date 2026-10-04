import { defineConfig } from "@playwright/test";
import { join } from "node:path";

const appDirectory = process.cwd().endsWith(join("frontend", "ecommerce-app"))
  ? process.cwd()
  : join(process.cwd(), "frontend", "ecommerce-app");

export default defineConfig({
  testDir: join(appDirectory, "e2e"),
  fullyParallel: false,
  workers: 1,
  reporter: "list",
  use: {
    baseURL: "http://127.0.0.1:4178",
    channel: "chrome",
    headless: true,
  },
  webServer: {
    command: `node "${join(appDirectory, "e2e/test-server.mjs")}"`,
    url: "http://127.0.0.1:4178",
    reuseExistingServer: false,
    timeout: 120_000,
  },
});
