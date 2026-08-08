/**
 * JobsMenu — Recent Jobs dropdown in the App header.
 *
 * Shows up to 20 session-scoped job history entries with status pills,
 * relative timestamps, and clickable rows that route to Run or Results.
 *
 * Created by: Adikarthik Gupta C B
 */
import { useState, useEffect, useRef } from "react";
import type { JobHistoryEntry, JobStatus } from "../hooks/useJobsHistory";
import { relativeTime } from "../lib/time";

interface StatusConfig {
  label: string;
  className: string;
}

const STATUS_CONFIG: Record<JobStatus, StatusConfig> = {
  running:   { label: "running",   className: "text-accent-cyan  border-accent-cyan/30  bg-accent-cyan/10" },
  completed: { label: "completed", className: "text-status-ok   border-status-ok/30   bg-status-ok/10" },
  stopped:   { label: "stopped",   className: "text-status-warn  border-status-warn/30  bg-status-warn/10" },
  failed:    { label: "failed",    className: "text-status-fail  border-status-fail/30  bg-status-fail/10" },
};

interface JobsMenuProps {
  jobs: JobHistoryEntry[];
  onNavigateToRun(entry: JobHistoryEntry): void;
  onNavigateToResults(entry: JobHistoryEntry): void;
}

function StatusPill({ status }: { status: JobStatus }): JSX.Element {
  const cfg = STATUS_CONFIG[status];
  return (
    <span className={`inline-block rounded-full border px-2 py-0.5 text-[10px] font-semibold ${cfg.className}`}>
      {cfg.label}
    </span>
  );
}

function formatEmails(emails: string[]): string {
  if (emails.length === 0) return "—";
  if (emails.length === 1) return emails[0];
  return `${emails[0]} +${emails.length - 1} more`;
}

export function JobsMenu({ jobs, onNavigateToRun, onNavigateToResults }: JobsMenuProps): JSX.Element {
  const [isOpen, setIsOpen] = useState(false);
  const panelRef = useRef<HTMLDivElement>(null);
  const buttonRef = useRef<HTMLButtonElement>(null);

  // Close on outside click
  useEffect(() => {
    if (!isOpen) return;

    function handleOutsideClick(event: MouseEvent): void {
      const target = event.target as Node;
      if (
        panelRef.current && !panelRef.current.contains(target) &&
        buttonRef.current && !buttonRef.current.contains(target)
      ) {
        setIsOpen(false);
      }
    }

    document.addEventListener("mousedown", handleOutsideClick);
    return () => document.removeEventListener("mousedown", handleOutsideClick);
  }, [isOpen]);

  function handleToggle(): void {
    setIsOpen(prev => !prev);
  }

  function handleRowClick(entry: JobHistoryEntry): void {
    setIsOpen(false);
    if (entry.status === "running") {
      onNavigateToRun(entry);
    } else {
      onNavigateToResults(entry);
    }
  }

  return (
    <div className="relative">
      <button
        ref={buttonRef}
        type="button"
        data-testid="jobs-menu-btn"
        onClick={handleToggle}
        className="btn-secondary text-xs px-3 py-1.5 flex items-center gap-1.5"
      >
        <span>Recent Jobs</span>
        {jobs.length > 0 && (
          <span className="inline-flex h-4 w-4 items-center justify-center rounded-full bg-accent-cyan/20 text-[9px] font-bold text-accent-cyan">
            {jobs.length > 9 ? "9+" : jobs.length}
          </span>
        )}
        <svg
          xmlns="http://www.w3.org/2000/svg"
          className={`h-3 w-3 transition-transform ${isOpen ? "rotate-180" : ""}`}
          fill="none"
          viewBox="0 0 24 24"
          stroke="currentColor"
          strokeWidth={2}
          aria-hidden="true"
        >
          <path strokeLinecap="round" strokeLinejoin="round" d="M19 9l-7 7-7-7" />
        </svg>
      </button>

      {isOpen && (
        <div
          ref={panelRef}
          data-testid="jobs-menu-panel"
          className="absolute right-0 top-full z-50 mt-2 w-96 rounded-card border border-white/10 bg-bg-accent/95 shadow-card backdrop-blur-md"
        >
          <div className="border-b border-white/10 px-4 py-2.5">
            <p className="text-xs font-mono uppercase tracking-widest text-text-muted">Recent Jobs</p>
          </div>

          {jobs.length === 0 ? (
            <div className="px-4 py-6 text-center text-sm text-text-muted">
              No jobs yet. Start one from the Setup screen.
            </div>
          ) : (
            <ul className="max-h-80 overflow-y-auto divide-y divide-white/5">
              {jobs.map(job => (
                <li key={`${job.jobId}-${job.startedAt}`}>
                  <button
                    type="button"
                    onClick={() => handleRowClick(job)}
                    className="w-full px-4 py-3 text-left hover:bg-white/[0.04] transition-colors"
                  >
                    <div className="flex items-center justify-between gap-3">
                      <StatusPill status={job.status} />
                      <span className="text-[10px] text-text-muted flex-shrink-0">
                        {relativeTime(job.startedAt)}
                      </span>
                    </div>
                    <p className="mt-1 truncate text-xs text-text-primary font-mono">
                      {formatEmails(job.emails)}
                    </p>
                  </button>
                </li>
              ))}
            </ul>
          )}
        </div>
      )}
    </div>
  );
}
