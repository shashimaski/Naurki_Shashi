/**
 * Stepper — three-node step indicator for setup / run / results flow.
 *
 * Props:
 *   current — which step is active: "setup" | "run" | "results"
 *
 * Created by: Adikarthik Gupta C B
 */

type StepId = "setup" | "run" | "results";

interface StepperProps {
  current: StepId;
}

const STEPS: { id: StepId; label: string }[] = [
  { id: "setup",   label: "Setup"   },
  { id: "run",     label: "Run"     },
  { id: "results", label: "Results" },
];

export function Stepper({ current }: StepperProps): JSX.Element {
  const currentIdx = STEPS.findIndex(s => s.id === current);

  return (
    <nav
      aria-label="progress"
      className="flex items-center justify-center gap-0 px-8 py-4"
      data-testid="stepper"
    >
      {STEPS.map((step, idx) => {
        const isActive   = step.id === current;
        const isComplete = idx < currentIdx;

        return (
          <div key={step.id} className="flex items-center">
            {/* Node */}
            <div
              data-testid={`step-${step.id}`}
              data-active={isActive ? "true" : "false"}
              data-complete={isComplete ? "true" : "false"}
              className={[
                "flex h-8 w-8 items-center justify-center rounded-full text-xs font-bold transition-all",
                isActive
                  ? "accent-gradient text-white shadow-[0_0_12px_2px_var(--accent)] ring-2 ring-accent/40"
                  : isComplete
                    ? "bg-accent/30 text-accent"
                    : "bg-white/10 text-text-muted",
              ].join(" ")}
            >
              {isComplete ? "✓" : idx + 1}
            </div>

            {/* Label */}
            <span
              className={[
                "ml-2 text-xs font-medium",
                isActive ? "text-accent" : "text-text-muted",
              ].join(" ")}
            >
              {step.label}
            </span>

            {/* Connector line (not after last) */}
            {idx < STEPS.length - 1 && (
              <div
                className={[
                  "mx-4 h-px w-12 transition-colors",
                  idx < currentIdx ? "bg-accent/50" : "bg-white/10",
                ].join(" ")}
              />
            )}
          </div>
        );
      })}
    </nav>
  );
}
