import { useEffect, useState } from "react";

// ── Types ─────────────────────────────────────────────────────────────────────

export interface ManualLoginCalloutProps {
  email: string;
  deadline: number;
  onContinue(): void;
  onSkip(): void;
}

// ── Helpers ───────────────────────────────────────────────────────────────────

function computeRemainingSeconds(deadline: number): number {
  return Math.max(0, Math.floor((deadline - Date.now()) / 1000));
}

// ── Component ─────────────────────────────────────────────────────────────────

export function ManualLoginCallout({
  email,
  deadline,
  onContinue,
  onSkip
}: ManualLoginCalloutProps): JSX.Element {
  const [remainingSeconds, setRemainingSeconds] = useState(() =>
    computeRemainingSeconds(deadline)
  );

  useEffect(() => {
    if (remainingSeconds <= 0) return;

    const intervalId = setInterval(() => {
      setRemainingSeconds(computeRemainingSeconds(deadline));
    }, 1000);

    return () => clearInterval(intervalId);
  }, [deadline, remainingSeconds]);

  return (
    <div
      data-testid="manual-callout"
      className="rounded-card border border-status-manual/40 bg-status-manual/10 p-5 shadow-card"
    >
      <p className="mb-1 text-sm font-semibold text-status-manual uppercase tracking-wide">
        Manual Login Required
      </p>

      <p className="mb-4 text-text-primary">
        Log into{" "}
        <span
          data-testid="manual-login-email"
          className="font-mono font-semibold text-accent-violet"
        >
          {email}
        </span>
        {" "}in the browser window.
      </p>

      <div className="mb-5 flex items-center gap-2 text-text-muted text-sm">
        <span>Time remaining:</span>
        <span
          data-testid="countdown"
          className="tabular-nums font-semibold text-text-primary"
        >
          {remainingSeconds}s
        </span>
      </div>

      <div className="flex gap-3">
        <button
          type="button"
          onClick={onContinue}
          className="rounded-lg bg-accent-violet px-4 py-2 text-sm font-semibold text-white hover:bg-accent-violet/80 transition-colors"
        >
          Continue
        </button>
        <button
          type="button"
          onClick={onSkip}
          className="rounded-lg border border-white/10 bg-white/5 px-4 py-2 text-sm font-semibold text-text-muted hover:bg-white/10 transition-colors"
        >
          Skip
        </button>
      </div>
    </div>
  );
}
