import { useEffect, useState } from "react";
import { useJobStream } from "../hooks/useJobStream";
import { stopJob } from "../api/rest";
import { RunTable } from "../components/RunTable";
import { ProgressRing } from "../components/ProgressRing";
import { ManualLoginCallout } from "../components/ManualLoginCallout";
import { continueJob, skipJob } from "../api/rest";

// ── Types ─────────────────────────────────────────────────────────────────────

export type RunSummary = {
  ok: number;
  authFailed: number;
  requiresManual: number;
  failed: number;
  skipped: number;
};

export interface RunScreenProps {
  jobId: string;
  total: number;
  onCompleted(summary: RunSummary): void;
  onStopped(): void;
}

// ── Helpers ───────────────────────────────────────────────────────────────────

function hasSummaryCounts(summary: RunSummary): boolean {
  return (
    summary.ok > 0 ||
    summary.authFailed > 0 ||
    summary.requiresManual > 0 ||
    summary.failed > 0 ||
    summary.skipped > 0
  );
}

// ── Component ─────────────────────────────────────────────────────────────────

export function RunScreen({
  jobId,
  total,
  onCompleted,
  onStopped
}: RunScreenProps): JSX.Element {
  const stream = useJobStream(jobId);
  const { byEmail, summary, awaitingManual, connectionState, events } = stream;

  const [isConfirmOpen, setIsConfirmOpen] = useState(false);
  const [connectionError, setConnectionError] = useState<string | null>(null);

  const accountRows = Object.values(byEmail);
  const completedCount = summary.ok + summary.authFailed + summary.requiresManual + summary.failed + summary.skipped;
  const displayTotal = summary.total || total;

  useEffect(() => {
    if (connectionState !== "closed") return;

    // Summary counts are the authoritative signal: job ran to completion.
    // Check this BEFORE the events-length guard so that tests and reconnect
    // scenarios (summary populated but events array empty) still navigate correctly.
    if (hasSummaryCounts(summary)) {
      onCompleted(summary);
      return;
    }

    // No summary counts. If we also received no events the WS closed before
    // the job started (connection never established) — show an error banner.
    if (events.length === 0) {
      setConnectionError(
        "WebSocket to the backend closed before any events arrived. " +
        "Check that the backend is running and /ws/jobs/<id> is reachable."
      );
      return;
    }

    // WS closed after events arrived but with no summary counts → job was stopped.
    onStopped();
  }, [connectionState, events.length, summary.ok, summary.authFailed, summary.requiresManual, summary.failed, summary.skipped]);

  const handleStopClick = (): void => {
    setIsConfirmOpen(true);
  };

  const handleConfirmStop = async (): Promise<void> => {
    setIsConfirmOpen(false);
    try {
      await stopJob(jobId);
    } catch {
      // best-effort — backend may be unreachable; navigate away regardless
    }
    onStopped();
  };

  const handleCancelStop = (): void => {
    setIsConfirmOpen(false);
  };

  const handleContinueManual = async (): Promise<void> => {
    await continueJob(jobId);
  };

  const handleSkipManual = async (): Promise<void> => {
    await skipJob(jobId);
  };

  return (
    <div data-testid="run-screen" className="flex min-h-screen flex-col bg-bg-base text-text-primary">
      {/* Header area */}
      <div className="flex items-center justify-between border-b border-white/10 px-6 py-4">
        <h1 className="font-head text-lg font-semibold text-text-primary">Running Job</h1>
        <button
          type="button"
          data-testid="stop"
          onClick={handleStopClick}
          className="rounded-lg border border-status-fail/40 bg-status-fail/10 px-4 py-2 text-sm font-semibold text-status-fail hover:bg-status-fail/20 transition-colors"
        >
          Stop
        </button>
      </div>

      {/* Connection error banner -- shown when the WS closed before any event arrived */}
      {connectionError && (
        <div role="alert" className="mx-6 mt-4 rounded-lg border border-status-fail/40 bg-status-fail/10 px-4 py-3 text-sm text-status-fail">
          <strong>Connection failed:</strong> {connectionError}
          {import.meta.env.DEV && (
            <span> In DEV mode this usually means the Vite proxy isn&apos;t forwarding the WS upgrade.</span>
          )}
          <div className="mt-3">
            <button
              type="button"
              data-testid="back-to-home"
              onClick={onStopped}
              className="rounded-lg border border-status-fail/40 bg-status-fail/10 px-3 py-1.5 text-xs font-semibold text-status-fail hover:bg-status-fail/20 transition-colors"
            >
              Back to Home
            </button>
          </div>
        </div>
      )}

      {/* Progress ring */}
      <div className="flex justify-center py-8">
        <ProgressRing value={completedCount} total={displayTotal} size={140} />
      </div>

      {/* Manual login callout */}
      {awaitingManual !== null && (
        <div className="mx-6 mb-6">
          <ManualLoginCallout
            email={awaitingManual.email}
            deadline={awaitingManual.deadline}
            onContinue={handleContinueManual}
            onSkip={handleSkipManual}
          />
        </div>
      )}

      {/* Account table */}
      <div className="flex-1 overflow-auto px-6 pb-24">
        <RunTable rows={accountRows} />
      </div>

      {/* Confirm stop dialog */}
      {isConfirmOpen && (
        <div className="fixed inset-0 z-50 flex items-center justify-center bg-black/60 backdrop-blur-sm">
          <div
            data-testid="confirm-dialog"
            className="w-full max-w-sm rounded-card bg-bg-accent p-6 shadow-card"
          >
            <h2 className="mb-3 font-head text-base font-semibold text-text-primary">
              Stop this job?
            </h2>
            <p className="mb-6 text-sm text-text-muted">
              Accounts that have not been processed will be skipped.
            </p>
            <div className="flex justify-end gap-3">
              <button
                type="button"
                data-testid="cancel-stop"
                onClick={handleCancelStop}
                className="rounded-lg border border-white/10 bg-white/5 px-4 py-2 text-sm font-semibold text-text-muted hover:bg-white/10 transition-colors"
              >
                Cancel
              </button>
              <button
                type="button"
                data-testid="confirm-stop"
                onClick={handleConfirmStop}
                className="rounded-lg bg-status-fail px-4 py-2 text-sm font-semibold text-white hover:bg-status-fail/80 transition-colors"
              >
                Stop Job
              </button>
            </div>
          </div>
        </div>
      )}
    </div>
  );
}
