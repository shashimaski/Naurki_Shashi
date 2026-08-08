/**
 * excel-upload.spec.ts — E2E spec for the Excel-upload flow.
 *
 * Flow:
 *   1. Spin up mock-naukri on a free port.
 *   2. Launch Electron with --e2e-mock=<mockUrl>.
 *   3. Generate a small XLSX in a temp dir via exceljs.
 *   4. Click "Upload Excel" tab → set the file input to the xlsx path.
 *   5. Wait for preview table to render 3 rows.
 *   6. Fill password + output folder → Click Start.
 *   7. Wait for results screen → assert count-ok = 3.
 *
 * Created by: Adikarthik Gupta C B
 */
import { test, expect } from "@playwright/test";
import { _electron as electron } from "@playwright/test";
import { spawn, ChildProcess } from "child_process";
import * as ExcelJS from "exceljs";
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

async function generateEmailsXlsx(dir: string): Promise<string> {
  const filePath = path.join(dir, "emails.xlsx");
  const wb = new ExcelJS.Workbook();
  const ws = wb.addWorksheet("Emails");
  ws.addRow(["Email", "Name"]);
  ws.addRow(["up1@x.com", "User 1"]);
  ws.addRow(["up2@x.com", "User 2"]);
  ws.addRow(["up3@x.com", "User 3"]);
  await wb.xlsx.writeFile(filePath);
  return filePath;
}

// ── Test ──────────────────────────────────────────────────────────────────────

test("excel-upload: three accounts from xlsx all complete OK", async () => {
  const mockPort = await getFreePort();
  const mockProc = spawnMock(mockPort);

  let app: Awaited<ReturnType<typeof electron.launch>> | null = null;

  try {
    await waitForMock(mockPort);

    const tempDir = fs.mkdtempSync(path.join(os.tmpdir(), "naukri-e2e-xl-"));
    const xlsxPath = await generateEmailsXlsx(tempDir);
    const outputDir = path.join(tempDir, "output");
    fs.mkdirSync(outputDir, { recursive: true });

    app = await electron.launch({
      args: [
        path.resolve(__dirname, "../../electron"),
        `--e2e-mock=http://127.0.0.1:${mockPort}`,
      ],
    });

    const page = await app.firstWindow();
    await page.waitForLoadState("domcontentloaded");

    // The default tab is "Upload Excel" — locate the file input inside ExcelDropzone
    // The ExcelDropzone uses a hidden file input; set its value via JS
    const fileInput = page.locator('input[type="file"]');
    // Use setInputFiles — Playwright will set the file on the input element
    await fileInput.setInputFiles(xlsxPath);

    // Wait for the preview table to show 3 rows (data rows, excluding header)
    // ExcelDropzone renders a table with rows per email; wait for at least 3 rows
    const tableRows = page.locator('table tbody tr');
    await expect(tableRows).toHaveCount(3, { timeout: 30_000 });

    // Fill password
    await page.locator('[data-testid="password"]').fill("test-password");

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

    // Wait for results screen (up to 180 s for 3 accounts)
    await page.locator('[data-testid="results-screen"]').waitFor({
      state: "visible",
      timeout: 180_000,
    });

    // Assert count-ok = 3
    const countOk = page.locator('[data-testid="count-ok"]');
    await expect(countOk).toContainText("3", { timeout: 10_000 });
  } finally {
    if (app) await app.close();
    mockProc.kill();
  }
});
