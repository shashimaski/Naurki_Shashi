/**
 * ResultsScreen — displays job summary tiles + per-account table after a run.
 *
 * Props:
 *   jobId         — the completed job's ID (used for CSV export URL)
 *   summary       — run-level counters
 *   accounts      — per-account final state
 *   outputFolder  — path to open in explorer
 *   onNewRun      — callback to reset back to setup
 *
 * Created by: Adikarthik Gupta C B
 */
import type { RunSummary } from "./RunScreen";
import type { AccountView } from "../hooks/useJobStream";

declare global {
  interface Window {
    electronAPI?: {
      pickFolder: (defaultPath?: string) => Promise<string | null>;
      openFolder: (path: string) => void;
      portInfo: () => number;
    };
  }
}

export interface ResultsScreenProps {
  jobId: string;
  summary: RunSummary;
  accounts: AccountView[];
  outputFolder: string;
  onNewRun(): void;
}

// ── Helpers ───────────────────────────────────────────────────────────────────

function getPort(): number {
  return (window as unknown as { NAUKRI_BE_PORT?: number }).NAUKRI_BE_PORT ?? 5000;
}

const STATUS_COLORS: Record<string, string> = {
  OK:              "text-status-ok   border-status-ok/30   bg-status-ok/10",
  AUTH_FAILED:     "text-status-fail border-status-fail/30 bg-status-fail/10",
  REQUIRES_MANUAL: "text-status-warn border-status-warn/30 bg-status-warn/10",
  FAILED:          "text-status-fail border-status-fail/30 bg-status-fail/10",
  SKIPPED:         "text-text-muted  border-white/10        bg-white/5",
};

function StatusPill({ status }: { status: string }): JSX.Element {
  const cls = STATUS_COLORS[status] ?? "text-text-muted border-white/10 bg-white/5";
  return (
    <span className={`inline-block rounded-full border px-2 py-0.5 text-xs font-semibold ${cls}`}>
      {status}
    </span>
  );
}

interface TileProps {
  label: string;
  count: number;
  testId: string;
  colorClass: string;
}

function SummaryTile({ label, count, testId, colorClass }: TileProps): JSX.Element {
  return (
    <div
      data-testid={testId}
      className={`flex flex-col items-center justify-center rounded-card border p-4 ${colorClass}`}
    >
      <span className="text-2xl font-bold">{count}</span>
      <span className="mt-1 text-xs font-medium opacity-80">{label}</span>
    </div>
  );
}

// ── Component ─────────────────────────────────────────────────────────────────

export function ResultsScreen({
  jobId,
  summary,
  accounts,
  outputFolder,
  onNewRun,
}: ResultsScreenProps): JSX.Element {

  async function handleOpenFolder(): Promise<void> {
    // Packaged Electron: use the OS-native folder open via preload IPC.
    if (window.electronAPI?.openFolder) {
      window.electronAPI.openFolder(outputFolder);
      return;
    }
    // DEV browser: no OS access. Copy the path so the user can paste it
    // into Windows Explorer's address bar.
    try {
      await navigator.clipboard.writeText(outputFolder);
      alert(`Report folder path copied to clipboard:\n\n${outputFolder}\n\nPaste it into Windows Explorer to open.`);
    } catch {
      alert(`Report folder:\n\n${outputFolder}\n\n(Copy the path manually -- clipboard access denied.)`);
    }
  }

  function handleExportCsv(): void {
    // DEV: relative URL, Vite proxy forwards to :8080.
    // Packaged Electron: absolute URL to the injected BE port.
    const url = import.meta.env.DEV
      ? `/api/runs/${jobId}/report.csv`
      : `http://127.0.0.1:${getPort()}/api/runs/${jobId}/report.csv`;
    const a = document.createElement("a");
    a.href = url;
    a.download = "report.csv";
    document.body.appendChild(a);
    a.click();
    document.body.removeChild(a);
  }

  return (
    <div data-testid="results-screen" className="flex flex-col gap-6 max-w-4xl mx-auto py-6 px-4">

      {/* ── Summary tiles ── */}
      <div className="grid grid-cols-5 gap-3">
        <SummaryTile
          testId="count-ok"
          label="OK"
          count={summary.ok}
          colorClass="border-status-ok/30 bg-status-ok/10 text-status-ok"
        />
        <SummaryTile
          testId="count-auth-failed"
          label="AUTH FAILED"
          count={summary.authFailed}
          colorClass="border-status-fail/30 bg-status-fail/10 text-status-fail"
        />
        <SummaryTile
          testId="count-requires-manual"
          label="MANUAL"
          count={summary.requiresManual}
          colorClass="border-status-warn/30 bg-status-warn/10 text-status-warn"
        />
        <SummaryTile
          testId="count-failed"
          label="FAILED"
          count={summary.failed}
          colorClass="border-status-fail/30 bg-status-fail/10 text-status-fail"
        />
        <SummaryTile
          testId="count-skipped"
          label="SKIPPED"
          count={summary.skipped}
          colorClass="border-white/10 bg-white/5 text-text-muted"
        />
      </div>

      {/* ── Account table ── */}
      {accounts.length > 0 && (
        <div className="overflow-x-auto rounded-card border border-white/10">
          <table className="w-full text-sm">
            <thead>
              <tr className="border-b border-white/10 text-left text-text-muted">
                <th className="px-4 py-3 font-medium">Email</th>
                <th className="px-4 py-3 font-medium">Status</th>
                <th className="px-4 py-3 font-medium">Resume rename</th>
              </tr>
            </thead>
            <tbody>
              {accounts.map((acct) => (
                <tr
                  key={acct.email}
                  className="border-b border-white/5 last:border-0 hover:bg-white/3"
                >
                  <td className="px-4 py-3 font-mono text-xs text-text-primary">{acct.email}</td>
                  <td className="px-4 py-3">
                    <StatusPill status={acct.status as string} />
                  </td>
                  <td className="px-4 py-3 text-xs text-text-muted">—</td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      )}

      {/* ── Action buttons ── */}
      <div className="flex flex-wrap gap-3">
        <button
          type="button"
          data-testid="open-folder"
          onClick={handleOpenFolder}
          className="btn-secondary px-4 py-2 text-sm"
        >
          Open report folder
        </button>
        <button
          type="button"
          data-testid="export-csv"
          onClick={handleExportCsv}
          className="btn-secondary px-4 py-2 text-sm"
        >
          Export CSV
        </button>
        <button
          type="button"
          data-testid="new-run"
          onClick={onNewRun}
          className="btn-primary px-4 py-2 text-sm font-semibold"
        >
          New run
        </button>
      </div>
    </div>
  );
}
