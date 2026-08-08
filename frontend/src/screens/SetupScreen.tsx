/**
 * SetupScreen — primary input screen for NaukriAutomator.
 *
 * Props:
 *   onStart — called with a complete StartJobRequest when the user clicks Start
 *
 * Layout:
 *   • Two tabs: "Upload Excel" (ExcelDropzone) / "Enter manually" (EmailChipInput)
 *   • Password field (masked)
 *   • Toggle: "Run browser visibly" (default ON → headless=false)
 *   • Toggle: "⚡ Log in manually for each account" (default OFF)
 *     — turning ON forces headless=false AND disables the headless toggle
 *   • Resume folder picker — path to a folder containing per-account resume
 *     files named `<Name>*.pdf`. When set, the backend uses the local file
 *     instead of downloading from Naukri.
 *   • Output folder picker
 *   • "Download Excel template" link
 *   • Sticky "Start" CTA — enabled when accounts.length > 0 AND password ≠ ""
 *     AND outputFolder ≠ "" AND resumeFolderPath ≠ ""
 *   • Live "Required to Start" checklist above the Start button
 *   • Backend health indicator pill
 *
 * Created by: Adikarthik Gupta C B
 */
import { useState } from "react";
import type { StartJobRequest, AccountInput } from "../api/types";
import type { ParsedEmailRow } from "../api/types";
import { downloadTemplate } from "../api/rest";
import EmailChipInput from "../components/EmailChipInput";
import ExcelDropzone from "../components/ExcelDropzone";
import { useBackendHealth } from "../hooks/useBackendHealth";

declare const __BUILD_TS__: string;

declare global {
  interface Window {
    electronAPI?: {
      pickFolder: (defaultPath?: string) => Promise<string | null>;
      openFolder: (path: string) => void;
      portInfo: () => number;
    };
  }
}

type Tab = "excel" | "manual";

interface Props {
  onStart: (request: StartJobRequest) => void;
  busy?: boolean;
}

const Required = () => <span className="ml-1 text-status-fail" aria-hidden="true">*</span>;

function ChecklistItem({ ok, label }: { ok: boolean; label: string }) {
  return (
    <li className="flex items-center gap-2">
      <span className={`inline-flex h-4 w-4 items-center justify-center rounded-full text-[10px] font-bold ${ok ? "bg-status-ok/20 text-status-ok" : "bg-status-fail/20 text-status-fail"}`}>
        {ok ? "✓" : "✕"}
      </span>
      <span className={ok ? "text-text-primary" : "text-text-muted"}>{label}</span>
    </li>
  );
}

const EMAIL_RE = /^[^\s@]+@[^\s@]+\.[^\s@]+$/;

/** Fallback name from an email address: local-part with dots/underscores → spaces, title-cased. */
function nameFromEmail(email: string): string {
  const local = email.split("@")[0] ?? email;
  return local
    .replace(/[._-]+/g, " ")
    .split(" ")
    .filter(Boolean)
    .map(word => word.charAt(0).toUpperCase() + word.slice(1))
    .join(" ") || local;
}

export default function SetupScreen({ onStart, busy = false }: Props) {
  const [tab, setTab]                       = useState<Tab>("excel");
  const [excelAccounts, setExcelAccounts]   = useState<AccountInput[]>([]);
  const [manualAccounts, setManualAccounts] = useState<AccountInput[]>([]);
  const [password, setPassword]             = useState("");
  const [visible, setVisible]               = useState(true);
  const [manualLogin, setManualLogin]       = useState(false);
  const [outputFolder, setOutputFolder]     = useState("");
  const [resumeFolderPath, setResumeFolderPath] = useState("");

  const beHealth = useBackendHealth();

  const accounts = tab === "excel" ? excelAccounts : manualAccounts;
  const headless = manualLogin ? false : !visible;

  const canStart =
    accounts.length > 0 &&
    password.trim() !== "" &&
    outputFolder.trim() !== "" &&
    resumeFolderPath.trim() !== "";

  function handleParsed(rows: ParsedEmailRow[]) {
    const valid = rows.filter(r => EMAIL_RE.test(r.email));
    setExcelAccounts(valid.map(r => ({
      email: r.email,
      name: (r.name && r.name.trim()) ? r.name.trim() : nameFromEmail(r.email),
    })));
  }

  async function handlePickFolder(setter: (v: string) => void) {
    if (window.electronAPI?.pickFolder) {
      const chosen = await window.electronAPI.pickFolder();
      if (chosen) setter(chosen);
    }
  }

  function handleStart() {
    if (!canStart) {
      console.warn("[Setup] Start clicked but canStart is false", {
        accountCount: accounts.length,
        hasPassword: password.length > 0,
        outputFolder,
        resumeFolderPath,
      });
      return;
    }
    console.info("[Setup] Start clicked, dispatching to App.handleStart", {
      accountCount: accounts.length,
      headless,
      manualLogin,
      resumeFolderPath,
    });
    onStart({
      accounts,
      password,
      headless,
      manualLogin,
      outputFolder,
      resumeFolderPath,
    });
  }

  return (
    <div className="flex flex-col gap-6 max-w-2xl mx-auto py-6 px-4">
      {/* ── Backend health indicator ── */}
      <div className="flex items-center gap-2 text-xs">
        <span className={`inline-block h-2 w-2 rounded-full ${beHealth === "ready" ? "bg-status-ok animate-pulse" : beHealth === "starting" ? "bg-status-warn animate-pulse" : "bg-status-fail"}`} />
        <span className="font-mono uppercase tracking-widest text-text-muted">
          Backend: {beHealth}
        </span>
      </div>

      {/* ── "Naukri accounts *" label above tab bar ── */}
      <div>
        <p className="text-sm text-text-muted mb-2">
          Naukri accounts (name + email)<Required />
        </p>

        {/* ── Tab bar ── */}
        <div role="tablist" className="flex gap-0 border-b border-white/10">
          <button
            role="tab"
            aria-selected={tab === "excel"}
            className={`px-4 py-2 text-sm transition-colors ${tab === "excel" ? "border-b-2 border-accent text-accent" : "text-text-muted hover:text-text"}`}
            onClick={() => setTab("excel")}
          >
            Upload Excel
          </button>
          <button
            role="tab"
            aria-selected={tab === "manual"}
            className={`px-4 py-2 text-sm transition-colors ${tab === "manual" ? "border-b-2 border-accent text-accent" : "text-text-muted hover:text-text"}`}
            onClick={() => setTab("manual")}
          >
            Enter manually
          </button>
        </div>

        {/* ── Tab panel ── */}
        <div className="mt-3">
          {tab === "excel" ? (
            <div>
              <ExcelDropzone onParsed={handleParsed} />
              <button
                type="button"
                role="button"
                className="mt-2 text-xs text-accent hover:underline"
                onClick={() => downloadTemplate()}
              >
                Download Excel template
              </button>
            </div>
          ) : (
            <EmailChipInput value={manualAccounts} onChange={setManualAccounts} />
          )}
        </div>
      </div>

      {/* ── Password ── */}
      <div className="flex flex-col gap-1">
        <label htmlFor="password-field" className="text-sm text-text-muted">
          Naukri Password<Required />
        </label>
        <input
          id="password-field"
          data-testid="password"
          type="password"
          autoComplete="current-password"
          className="input"
          placeholder="&#x2022;&#x2022;&#x2022;&#x2022;&#x2022;&#x2022;&#x2022;&#x2022;"
          value={password}
          onChange={e => setPassword(e.target.value)}
        />
      </div>

      {/* ── Toggles ── */}
      <div className="flex flex-col gap-3">
        <label className="flex items-center gap-3 cursor-pointer select-none">
          <input
            type="checkbox"
            role="checkbox"
            aria-label="Run browser visibly"
            checked={visible}
            disabled={manualLogin}
            onChange={e => setVisible(e.target.checked)}
            className="w-4 h-4 accent-[var(--accent)]"
          />
          <span className={`text-sm ${manualLogin ? "text-text-muted/50" : "text-text"}`}>
            Run browser visibly
          </span>
        </label>

        <label className="flex items-center gap-3 cursor-pointer select-none">
          <input
            type="checkbox"
            role="checkbox"
            aria-label="&#x26A1; Log in manually for each account"
            checked={manualLogin}
            onChange={e => setManualLogin(e.target.checked)}
            className="w-4 h-4 accent-[var(--accent)]"
          />
          <span className="text-sm text-text">
            &#x26A1; Log in manually for each account
          </span>
        </label>
      </div>

      {/* ── Resume folder ── */}
      <div className="flex flex-col gap-1">
        <label htmlFor="resume-folder-field" className="text-sm text-text-muted">
          Resume folder<Required />
          <span className="ml-2 text-xs text-text-muted/70">
            (folder with per-account resume files named <code>&lt;Name&gt;*.pdf</code>)
          </span>
        </label>
        <div className="flex gap-2">
          <input
            id="resume-folder-field"
            data-testid="resume-folder"
            type="text"
            readOnly={!!window.electronAPI}
            className="input flex-1"
            placeholder="C:\resumes"
            value={resumeFolderPath}
            onChange={e => !window.electronAPI && setResumeFolderPath(e.target.value)}
          />
          {window.electronAPI && (
            <button
              type="button"
              className="btn-secondary text-sm px-3"
              onClick={() => handlePickFolder(setResumeFolderPath)}
            >
              Browse&#x2026;
            </button>
          )}
        </div>
      </div>

      {/* ── Output folder ── */}
      <div className="flex flex-col gap-1">
        <label htmlFor="output-folder-field" className="text-sm text-text-muted">
          Output folder<Required />
        </label>
        <div className="flex gap-2">
          <input
            id="output-folder-field"
            data-testid="output-folder"
            type="text"
            readOnly={!!window.electronAPI}
            className="input flex-1"
            placeholder="C:\runs\naukri"
            value={outputFolder}
            onChange={e => !window.electronAPI && setOutputFolder(e.target.value)}
          />
          {window.electronAPI && (
            <button
              type="button"
              className="btn-secondary text-sm px-3"
              onClick={() => handlePickFolder(setOutputFolder)}
            >
              Browse&#x2026;
            </button>
          )}
        </div>
      </div>

      {/* ── Required to Start checklist ── */}
      <div className="mt-2 rounded-lg border border-white/10 bg-white/[0.02] px-4 py-3">
        <p className="text-xs font-mono uppercase tracking-widest text-text-muted mb-2">Required to Start</p>
        <ul className="space-y-1 text-sm">
          <ChecklistItem ok={accounts.length > 0} label={`At least one account (${accounts.length} added)`} />
          <ChecklistItem ok={password.trim().length > 0} label="Naukri password" />
          <ChecklistItem ok={resumeFolderPath.trim().length > 0} label="Resume folder" />
          <ChecklistItem ok={outputFolder.trim().length > 0} label="Output folder" />
        </ul>
      </div>

      {/* ── Start CTA ── */}
      <button
        data-testid="start"
        type="button"
        disabled={!canStart || busy}
        className="btn-primary w-full py-3 text-base font-semibold disabled:opacity-40 disabled:cursor-not-allowed mt-2"
        onClick={handleStart}
      >
        {busy ? "Starting…" : "Start"}
      </button>

      {/* ── Build timestamp footer ── */}
      <p className="mt-4 text-center text-[10px] font-mono text-text-muted opacity-60">
        Build {__BUILD_TS__.slice(0, 16).replace("T", " ")} &middot; by Adikarthik Gupta C B
      </p>
    </div>
  );
}
