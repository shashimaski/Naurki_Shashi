import { motion } from "framer-motion";
import { clsx } from "clsx";
import type { AccountView } from "../hooks/useJobStream";

// ── Types ─────────────────────────────────────────────────────────────────────

export interface RunTableProps {
  rows: AccountView[];
}

// ── Status pill styling ───────────────────────────────────────────────────────

type PillConfig = {
  className: string;
  label: string;
};

const STATUS_PILL_MAP: Record<AccountView["status"], PillConfig> = {
  OK:              { className: "bg-status-ok text-white",                  label: "OK"             },
  AUTH_FAILED:     { className: "bg-status-fail text-white",                label: "AUTH_FAILED"    },
  REQUIRES_MANUAL: { className: "bg-status-manual text-white",              label: "REQUIRES_MANUAL"},
  FAILED:          { className: "bg-status-fail text-white",                label: "FAILED"         },
  SKIPPED:         { className: "bg-text-muted/30 text-text-muted",         label: "SKIPPED"        },
  RUNNING:         { className: "bg-accent-cyan/20 text-accent-cyan",       label: "RUNNING"        },
  PENDING:         { className: "bg-text-muted/20 text-text-muted",         label: "PENDING"        }
};

// ── Sub-components ────────────────────────────────────────────────────────────

interface StatusPillProps {
  status: AccountView["status"];
}

function StatusPill({ status }: StatusPillProps): JSX.Element {
  const config = STATUS_PILL_MAP[status] ?? { className: "bg-text-muted/20 text-text-muted", label: status };
  const isRunning = status === "RUNNING";

  return (
    <motion.span
      key={status}
      initial={{ opacity: 0, scale: 0.85 }}
      animate={{ opacity: 1, scale: 1 }}
      transition={{ duration: 0.18 }}
      className={clsx(
        "inline-flex items-center gap-1.5 rounded-full px-2.5 py-0.5 text-xs font-semibold",
        config.className
      )}
    >
      {isRunning && (
        <span className="inline-block h-2 w-2 rounded-full bg-accent-cyan animate-pulse" />
      )}
      {config.label}
    </motion.span>
  );
}

// ── Main component ────────────────────────────────────────────────────────────

export function RunTable({ rows }: RunTableProps): JSX.Element {
  return (
    <div className="overflow-x-auto rounded-card bg-bg-accent shadow-card">
      <table className="w-full text-sm text-text-primary">
        <thead>
          <tr className="border-b border-white/10 text-left text-text-muted">
            <th className="px-4 py-3 font-medium">#</th>
            <th className="px-4 py-3 font-medium">Email</th>
            <th className="px-4 py-3 font-medium">Status</th>
            <th className="px-4 py-3 font-medium">Current Step</th>
            <th className="px-4 py-3 font-medium">Elapsed</th>
          </tr>
        </thead>
        <tbody>
          {rows.map((row) => (
            <tr
              key={row.email}
              className="border-b border-white/5 hover:bg-white/5 transition-colors"
            >
              <td className="px-4 py-3 text-text-muted">{row.index + 1}</td>
              <td className="px-4 py-3 font-mono">{row.email}</td>
              <td className="px-4 py-3">
                <StatusPill status={row.status} />
              </td>
              <td className="px-4 py-3 text-text-muted font-mono text-xs">
                {row.currentStep || "—"}
              </td>
              <td className="px-4 py-3 text-text-muted tabular-nums">
                {row.elapsedMs > 0 ? `${(row.elapsedMs / 1000).toFixed(1)}s` : "—"}
              </td>
            </tr>
          ))}
        </tbody>
      </table>
    </div>
  );
}
