/**
 * manual-login.spec.ts — E2E spec for the manual-login flow.
 *
 * Flow:
 *   1. Spin up mock-naukri on a free port.
 *   2. Launch Electron with --e2e-mock=<mockUrl>.
 *   3. Enter one email otp@x.com + toggle manual-login ON.
 *   4. Click Start → wait for ManualLoginCallout text.
 *   5. POST /otp on the mock (simulates user completing OTP in browser).
 *   6. Click "Continue" in the callout.
 *   7. Wait for results screen → assert count-ok = 1.
 *
 * Created by: Adikarthik Gupta C B
 */
import { test, expect } from "@playwright/test";
import { _electron as electron } from "@playwright/test";
import { spawn, ChildProcess } from "child_process";
import * as fs from "fs";
import * as net from "net";
import * as os from "os";
import * as path from "path";

// ── Helpers ───────────────────────────────────────────────────────────────────

async function getFreePort(): Promise<number> {
  return new Promise((resolve, reject) => {
    const srv = net.createServer();
    srv.listen(0, "127.0.0.1", () => {
      const addr = srv.address();
      const port = typeof addr === "object" && addr ? addr.port : 0;
      srv.close(() => resolve(port));
    });
    srv.on("error", reject);
  });
}

async function waitForMock(port: number, maxWaitMs = 30_000): Promise<void> {
  const start = Date.now();
  while (Date.now() - start < maxWaitMs) {
    try {
      const resp = await fetch(`http://127.0.0.1:${port}/nlogin/login`);
      if (resp.ok || resp.status === 200) return;
    } catch {
      // not ready yet
    }
    await new Promise((r) => setTimeout(r, 500));
  }
  throw new Error(`Mock did not become ready on port ${port} within ${maxWaitMs}ms`);
}

function spawnMock(port: number): ChildProcess {
  const jarPath = path.resolve(
    __dirname,
    "../../mock-naukri/target/mock-naukri.jar"
  );
  return spawn("java", ["-jar", jarPath, `--server.port=${port}`], {
    stdio: "ignore",
  });
}

// ── Test ──────────────────────────────────────────────────────────────────────

test("manual-login: otp account completes OK after OTP posted to mock", async () => {
  const mockPort = await getFreePort();
  const mockProc = spawnMock(mockPort);

  let app: Awaited<ReturnType<typeof electron.launch>> | null = null;

  try {
    await waitForMock(mockPort);

    const outputDir = fs.mkdtempSync(path.join(os.tmpdir(), "naukri-e2e-ml-"));

    app = await electron.launch({
      args: [
        path.resolve(__dirname, "../../electron"),
        `--e2e-mock=http://127.0.0.1:${mockPort}`,
      ],
    });

    const page = await app.firstWindow();
    await page.waitForLoadState("domcontentloaded");

    // Switch to manual entry tab
    await page.getByRole("tab", { name: /enter manually/i }).click();

    // Add otp email
    const chipInput = page.locator('[data-testid="chip-input"]');
    await chipInput.fill("otp@x.com");
    await chipInput.press("Enter");

    // Fill password
    await page.locator('[data-testid="password"]').fill("test-password");

    // Toggle manual login ON
    const manualLoginToggle = page.getByRole("checkbox", {
      name: /log in manually for each account/i,
    });
    await manualLoginToggle.check();

    // Set output folder
    const outputInput = page.locator('[data-testid="output-folder"]');
    await outputInput.evaluate(
      (el: HTMLInputElement, val: string) => {
        el.removeAttribute("readonly");
        el.value = val;
        el.dispatchEvent(new Event("input", { bubbles: true }));
        el.dispatchEvent(new Event("change", { bubbles: true }));
      },
      outputDir
    );

    // Click Start
    await page.locator('[data-testid="start"]').click();

    // Wait for ManualLoginCallout to appear with the email text
    await expect(page.getByText(/Log into otp@x\.com/i, { exact: false })).toBeVisible({
      timeout: 60_000,
    });

    // Complete the OTP on the mock server (simulates user action in separate browser)
    const otpResp = await fetch(`http://127.0.0.1:${mockPort}/otp`, {
      method: "POST",
      headers: { "Content-Type": "application/x-www-form-urlencoded" },
      body: "otp=123456",
      redirect: "manual",
    });
    // mock redirects to /mnjuser/homepage — either 200 or 3xx is fine
    expect([200, 302, 303]).toContain(otpResp.status);

    // Click Continue in the callout
    const continueBtn = page.getByRole("button", { name: /continue/i });
    await continueBtn.click();

    // Wait for results screen
    await page.locator('[data-testid="results-screen"]').waitFor({
      state: "visible",
      timeout: 180_000,
    });

    // Assert count-ok = 1
    const countOk = page.locator('[data-testid="count-ok"]');
    await expect(countOk).toContainText("1", { timeout: 10_000 });
  } finally {
    if (app) await app.close();
    mockProc.kill();
  }
});
