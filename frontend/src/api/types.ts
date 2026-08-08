export type AccountStatus = "OK" | "AUTH_FAILED" | "REQUIRES_MANUAL" | "FAILED" | "SKIPPED";

export interface AccountInput {
  name: string;
  email: string;
}

export interface StartJobRequest {
  accounts: AccountInput[];
  password: string;
  headless: boolean;
  manualLogin: boolean;
  outputFolder: string;
  /** Optional path to a folder containing per-account resume files named `<name>*.pdf`.
   *  When set, the backend skips the Naukri download and uses the local file. */
  resumeFolderPath?: string;
  baseUrlOverride?: string;
}

export interface StartJobResponse {
  jobId: string;
  wsUrl: string;
}

export interface ParsedEmailRow {
  email: string;
  name?: string;
  rowIndex: number;
}

export type JobEvent =
  | { type: "RUN_STARTED"; jobId: string; timestamp: string; total: number }
  | { type: "ACCOUNT_STARTED"; jobId: string; timestamp: string; email: string; index: number }
  | { type: "STEP_STARTED"; jobId: string; timestamp: string; email: string; step: string }
  | { type: "STEP_COMPLETED"; jobId: string; timestamp: string; email: string; step: string; durationMs: number }
  | { type: "STEP_FAILED"; jobId: string; timestamp: string; email: string; step: string; error: string }
  | { type: "AWAIT_MANUAL_LOGIN"; jobId: string; timestamp: string; email: string; timeoutSec: number }
  | { type: "ACCOUNT_COMPLETED"; jobId: string; timestamp: string; email: string; status: AccountStatus; resumeOldName?: string; resumeNewName?: string }
  | { type: "RUN_COMPLETED"; jobId: string; timestamp: string; summary: { ok: number; authFailed: number; requiresManual: number; failed: number; skipped: number } }
  | { type: "RUN_STOPPED"; jobId: string; timestamp: string };
