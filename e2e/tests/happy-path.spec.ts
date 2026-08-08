/**
 * happy-path.spec.ts — full happy-path E2E test.
 *
 * Flow:
 *   1. Spin up mock-naukri on a free port.
 *   2. Launch Electron with --e2e-mock=<mockUrl>.
 *   3. Enter two valid emails + password + output folder.
 *   4. Click Start → wait for results screen → assert count-ok = 2.
 *   5. Assert report.csv exists under the output folder.
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

function findReportCsv(dir: string): string | null {
  if (!fs.existsSync(dir)) return null;
  const entries = fs.readdirSync(dir, { withFileTypes: true });
  for (const entry of entries) {
    const fullPath = path.join(dir, entry.name);
    if (entry.isDirectory()) {
      const found = findReportCsv(fullPath);
      if (found) return found;
    } else if (entry.name === "report.csv") {
      return fullPath;
    }
  }
  return null;
}

// ── Test ──────────────────────────────────────────────────────────────────────

test("happy-path: two accounts complete OK and report.csv is written", async () => {
  const mockPort = await getFreePort();
  const mockProc = spawnMock(mockPort);

  let app: Awaited<ReturnType<typeof electron.launch>> | null = null;

  try {
    await waitForMock(mockPort);

    const outputDir = fs.mkdtempSync(path.join(os.tmpdir(), "naukri-e2e-hp-"));

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

    // Add two emails via chip input
    const chipInput = page.locator('[data-testid="chip-input"]');
    await chipInput.fill("ok@x.com");
    await chipInput.press("Enter");
    await chipInput.fill("ok2@x.com");
    await chipInput.press("Enter");

    // Fill password
    await page.locator('[data-testid="password"]').fill("test-password");

    // Set output folder — in E2E the electronAPI.pickFolder is not easily triggered,
    // so we fill the text input directly (readOnly guard only blocks when electronAPI exists
    // but the preload in dev mode should still allow fill via JS)
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

    // Wait for results screen (up to 180 s)
    await page.locator('[data-testid="results-screen"]').waitFor({
      state: "visible",
      timeout: 180_000,
    });

    // Assert count-ok = 2
    const countOk = page.locator('[data-testid="count-ok"]');
    await expect(countOk).toContainText("2", { timeout: 10_000 });

    // Assert report.csv exists somewhere under outputDir
    const csvPath = findReportCsv(outputDir);
    expect(
      csvPath,
      `report.csv not found under ${outputDir}`
    ).toBeTruthy();
  } finally {
    if (app) await app.close();
    mockProc.kill();
  }
});
