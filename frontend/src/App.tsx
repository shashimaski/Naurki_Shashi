/**
 * App — top-level router for NaukriAutomator.
 *
 * Screens: setup → run → results (→ back to setup via "New run")
 *
 * Created by: Adikarthik Gupta C B
 */
import { useState } from "react";
import { startJob, stopJob } from "./api/rest";
import type { StartJobRequest } from "./api/types";
import SetupScreen from "./screens/SetupScreen";
import { RunScreen }     from "./screens/RunScreen";
import { ResultsScreen } from "./screens/ResultsScreen";
import { Stepper }       from "./components/Stepper";
import { JobsMenu }      from "./components/JobsMenu";
import type { RunSummary }   from "./screens/RunScreen";
import type { AccountView }  from "./hooks/useJobStream";
import { useJobsHistory } from "./hooks/useJobsHistory";
import type { JobHistoryEntry } from "./hooks/useJobsHistory";

type Screen = "setup" | "run" | "results";

interface RunContext {
  jobId?:       string;
  total:        number;
  outputFolder: string;
  summary?:     RunSummary;
  accounts?:    AccountView[];
  emails?:      string[];
}

const EMPTY_CONTEXT: RunContext = { total: 0, outputFolder: "" };

export default function App(): JSX.Element {
  const [screen,     setScreen]     = useState<Screen>("setup");
  const [ctx,        setCtx]        = useState<RunContext>(EMPTY_CONTEXT);
  const [starting,   setStarting]   = useState(false);
  const [startError, setStartError] = useState<string | null>(null);

  const { jobs, addJob, updateJobStatus } = useJobsHistory();

  async function handleStart(req: StartJobRequest): Promise<void> {
    setStarting(true);
    setStartError(null);
    const emails = req.accounts.map(a => a.email);
    try {
      const resp = await startJob(req);
      setCtx({
        jobId: resp.jobId,
        total: emails.length,
        outputFolder: req.outputFolder,
        emails,
      });
      addJob({
        jobId: resp.jobId,
        emails,
        status: "running",
        outputFolder: req.outputFolder,
      });
      setScreen("run");
    } catch (e) {
      const msg = e instanceof Error ? e.message : String(e);
      setStartError(msg || "Failed to start the run. Check the backend is running.");
    } finally {
      setStarting(false);
    }
  }

  function handleCompleted(summary: RunSummary, accounts: AccountView[]): void {
    setCtx(prev => ({ ...prev, summary, accounts }));
    if (ctx.jobId) updateJobStatus(ctx.jobId, "completed", summary);
    setScreen("results");
  }

  function handleStopped(): void {
    if (ctx.jobId) updateJobStatus(ctx.jobId, "stopped");
    setScreen("results");
  }

  function handleNewRun(): void {
    setCtx(EMPTY_CONTEXT);
    setScreen("setup");
  }

  async function handleHomeClick(): Promise<void> {
    if (screen === "setup") {
      // Already home — subtle flash handled by the button disabled state
      return;
    }
    if (screen === "run" && ctx.jobId) {
      const confirmed = window.confirm("Job in progress. Return to setup?");
      if (!confirmed) return;
      try {
        await stopJob(ctx.jobId);
      } catch {
        // best-effort — ignore stop errors
      }
      if (ctx.jobId) updateJobStatus(ctx.jobId, "stopped");
    }
    setCtx(EMPTY_CONTEXT);
    setScreen("setup");
  }

  function handleNavigateToRun(entry: JobHistoryEntry): void {
    setCtx({
      jobId: entry.jobId,
      total: entry.emails.length,
      outputFolder: entry.outputFolder,
      emails: entry.emails,
    });
    setScreen("run");
  }

  function handleNavigateToResults(entry: JobHistoryEntry): void {
    setCtx({
      jobId: entry.jobId,
      total: entry.emails.length,
      outputFolder: entry.outputFolder,
      summary: entry.summary,
      emails: entry.emails,
    });
    setScreen("results");
  }

  return (
    <div className="min-h-screen flex flex-col">
      {/* ── Header ── */}
      <header className="px-8 py-4 flex items-center justify-between border-b border-white/5">
        <div>
          <h1 className="text-2xl accent-gradient">NAUKRI_AUTOMATOR</h1>
          <p className="text-xs text-text-muted">by Adikarthik Gupta C B</p>
        </div>
        <div className="flex items-center gap-3">
          <button
            type="button"
            data-testid="home-btn"
            onClick={handleHomeClick}
            disabled={screen === "setup"}
            className="btn-secondary text-xs px-3 py-1.5 disabled:opacity-40 disabled:cursor-default"
          >
            Home
          </button>
          <JobsMenu
            jobs={jobs}
            onNavigateToRun={handleNavigateToRun}
            onNavigateToResults={handleNavigateToResults}
          />
          <span className="text-xs text-text-muted mono">v0.1.0</span>
        </div>
      </header>

      {/* ── Stepper ── */}
      <Stepper current={screen} />

      {/* ── Start error banner ── */}
      {startError && screen === "setup" && (
        <div role="alert" className="mx-8 mt-4 rounded-lg border border-status-fail/40 bg-status-fail/10 px-4 py-3 text-sm text-status-fail">
          <strong>Could not start:</strong> {startError}
        </div>
      )}

      {/* ── Screen content ── */}
      <main className="flex-1 p-8">
        {screen === "setup" && (
          <SetupScreen onStart={handleStart} busy={starting} />
        )}

        {screen === "run" && ctx.jobId && (
          <RunScreen
            jobId={ctx.jobId}
            total={ctx.total}
            onCompleted={(summary) => {
              handleCompleted(summary, []);
            }}
            onStopped={handleStopped}
          />
        )}

        {screen === "results" && ctx.jobId && (
          <ResultsScreen
            jobId={ctx.jobId}
            summary={ctx.summary ?? { ok: 0, authFailed: 0, requiresManual: 0, failed: 0, skipped: 0 }}
            accounts={ctx.accounts ?? []}
            outputFolder={ctx.outputFolder}
            onNewRun={handleNewRun}
          />
        )}
      </main>
    </div>
  );
}
