---
title: NaukriAutomator — Design Spec
status: approved
date: 2026-07-14
author: Adikarthik Gupta C B
---

# NaukriAutomator — Design Spec

**Author:** Adikarthik Gupta C B
**Date:** 2026-07-14
**Working directory:** `F:\views\g\Naukri`
**Status:** Approved for implementation planning

---

## 1. Purpose

A Windows utility (single `.exe`) that refreshes the "last updated" timestamp of one or more Naukri.com profiles by:

1. Accepting a list of Naukri account emails (from an Excel upload or manual entry) plus a common password.
2. Spawning a browser agent per account (sequentially, incognito).
3. Logging in (fully automated **or** user-assisted manual login).
4. Making a net-zero edit to the profile headline (append space, save; remove space, save).
5. Downloading the current resume, renaming it with today's date, and re-uploading it.
6. Logging out cleanly.
7. Producing a per-run report (CSV + JSON) plus per-account logs and screenshots.

---

## 2. High-level architecture

Single `.exe` produced by **electron-builder**. Two runtime processes:

| Process | Language / stack | Responsibility |
|---|---|---|
| Electron **main** | Node | App lifecycle, window, spawns Java child, IPC to renderer, file dialogs, port selection |
| Electron **renderer** | React 18 + Vite + TypeScript + Tailwind + shadcn/ui + framer-motion | All UI (Setup · Run · Results) |
| Java **backend** | Spring Boot 3, Java 17, Playwright-Java, Apache POI | REST + WebSocket API, job orchestrator, automation, Excel parsing, report writing |

Communication:

- FE ↔ BE over `http://127.0.0.1:<random-free-port>` (chosen at launch; passed to renderer via Electron IPC).
- REST for commands (`POST /api/jobs`, `POST /api/jobs/{id}/stop`, `POST /api/jobs/{id}/continue`, `GET /api/template`).
- WebSocket `ws://127.0.0.1:<port>/ws/jobs/{jobId}` for progress events.

---

## 3. Runtime bundle inside the EXE

```
resources/
├── jre/                 Portable Temurin JRE 17 (Windows x64, ~45 MB)
├── backend/
│   └── naukri-be.jar    Spring Boot fat jar (~35 MB)
├── playwright/
│   └── chromium/        Chromium only (Firefox / WebKit skipped, ~120 MB)
└── templates/
    └── naukri-emails-template.xlsx
```

Expected installer size: ~200 MB (acceptable for a Windows desktop utility).

---

## 4. Automation flow (per account, sequential)

Steps executed by `NaukriAutomator` on the BE. Each step returns a `StepResult` so failures pinpoint precisely which step broke.

1. Launch a fresh **incognito** Playwright `BrowserContext` (no persisted storage; per-account throwaway user-data-dir).
2. Navigate to `https://www.naukri.com/nlogin/login`.
3. **Login branch:**
   - If `manualLoginMode = false` → fill email + password, submit.
   - If `manualLoginMode = true`:
     - Bring the browser window to front.
     - Emit `AWAIT_MANUAL_LOGIN{email}` on the WebSocket.
     - Poll `page.url()` every 1 s. Resume when URL matches `/(mnjuser|homepage|profile|dashboard)/i`.
     - Also resume immediately on `POST /api/jobs/{id}/continue` from the FE.
     - Cancel this account on `POST /api/jobs/{id}/skip`.
     - Timeout: **5 minutes** → mark `REQUIRES_MANUAL` and skip.
4. **Post-login branch detection:**
   - Dashboard reached → continue to step 5.
   - OTP / captcha page → status `REQUIRES_MANUAL`, close context, skip.
   - Wrong-creds banner → status `AUTH_FAILED`, close context, skip.
5. Open profile → click Headline edit → append `" "` → Save.
6. Click Headline edit again → strip trailing space → Save. (Net-zero change, but `lastUpdated` refreshes.)
7. Scroll to "Attach Resume" → click "Download" → capture Playwright download event → save to a temp dir with the server-provided filename.
8. Rename the downloaded file to `<original-stem>_YYYY-MM-DD.<ext>` using `LocalDate.now()`.
9. Click "Update resume" → upload the renamed file → wait for the success toast.
10. Open user menu → Logout → wait for the login page to reappear.
11. Close the `BrowserContext`.

---

## 5. Retry policy

- Any failure in steps 3–11 → retry the **whole account** once with 2× default timeouts.
- If headless mode was chosen and the retry also failed → one final attempt in **headed** mode.
- Third failure → status `FAILED`; capture the last error message and a screenshot at `<outputFolder>/<runTimestamp>/screenshots/<email>.png` (see §9); continue to the next account.
- In `manualLoginMode`, retries also re-enter the manual-login wait.

---

## 6. Frontend screens (React)

Three flat screens connected by a stepper — no left sidebar.

### 6.1 Setup

- Two tabs:
  - **Upload Excel** — drop-zone + preview table + invalid-row panel.
  - **Enter manually** — chip input for email addresses.
- Common password field (masked; in-memory only; cleared on window close).
- Toggle: `Run browser visibly` (default on) ↔ headless.
- Toggle: `⚡ Log in manually for each account` (default off).
- Output folder picker (default: `%USERPROFILE%/NaukriAutomator/runs/<timestamp>`).
- Link: "Download Excel template" (streams `templates/naukri-emails-template.xlsx`).
- Primary CTA `Start` enabled when ≥ 1 valid email AND non-empty password.

### 6.2 Run

- Live per-account table: `email · current step · elapsed · status pill`.
- Central circular progress ring with `x / N` centred.
- Stop button — aborts the active context; remaining accounts marked `SKIPPED`.
- **Paused-for-login state** on the active row (when `manualLoginMode` is on):
  - Callout: *"Log into `<email>` in the open browser — I'll resume automatically when the dashboard loads."*
  - Countdown to the 5-minute timeout.
  - Buttons: `Continue now` and `Skip this account`.

### 6.3 Results

- Final table with status pills (`OK`, `AUTH_FAILED`, `REQUIRES_MANUAL`, `FAILED`, `SKIPPED`).
- Summary counts.
- Buttons: `Open report folder`, `Export CSV`, `New run`.

---

## 7. Visual language

Design tokens live in `frontend/src/theme/tokens.ts` and drive both the Tailwind config and the shadcn/ui theme.

```
bg-base          : linear-gradient(180deg, #050915 0%, #0b1226 100%)
bg-card          : rgba(255,255,255,.04) + backdrop-filter: blur(14px)
border-card      : rgba(148,163,184,.12)
accent-a         : #22d3ee   (cyan)
accent-b         : #a855f7   (violet)
accent-gradient  : linear-gradient(90deg, accent-a, accent-b)
text-primary     : #e6edf3
text-muted       : #94a3b8
status-ok        : #22c55e
status-warn      : #f59e0b
status-fail      : #ef4444
status-manual    : #a855f7
radius-card      : 16px
shadow-card      : 0 8px 30px rgba(0,0,0,.35)
font-head        : "JetBrains Mono", ui-monospace, monospace  (weight 600, tracking -0.02em)
font-body        : "Inter", system-ui, sans-serif             (weight 400 / 500)
motion           : framer-motion, ease-out, 200–300 ms
```

Chrome:

- Slim hero band on top: `NAUKRI_AUTOMATOR` in mono with a gradient underline; version + byline "by Adikarthik Gupta C B" on the right.
- Sticky bottom action bar (primary CTA always reachable).
- Status pills use the status tokens above.

Stack: **Vite + React 18 + TypeScript + Tailwind CSS + shadcn/ui + framer-motion + lucide-react**.

---

## 8. Excel template

File: `naukri-emails-template.xlsx` (bundled in `resources/templates/` and downloadable from the Setup screen).

- Sheet name: `Emails`.
- Row 1 headers: **`email`** (required), `remarks` (optional; ignored by the parser).
- 5 sample rows pre-filled with placeholder emails so users see the expected shape.
- Parsed by Apache POI on the BE.
- Validation surface: rows with empty or malformed emails are shown in an "invalid rows" panel before `Start` is enabled.

---

## 9. Reports (written on run completion)

Under `<outputFolder>/<runTimestamp>/`:

- `report.csv` — one row per account (email, status, error, resumeOldName, resumeNewName, startedAt, endedAt, retries).
- `report.json` — same data plus per-step timings.
- `logs/<email>.log` — plain-text step log per account.
- `screenshots/<email>.png` — only if that account's final status is `FAILED`.

---

## 10. Data contracts

### 10.1 REST

- `POST /api/jobs`
  - Request: `{ emails: string[], password: string, headless: boolean, manualLogin: boolean, outputFolder: string }`
  - Response: `{ jobId: string, wsUrl: string }`
- `POST /api/jobs/{id}/stop` → aborts the run.
- `POST /api/jobs/{id}/continue` → resumes a `AWAIT_MANUAL_LOGIN` state early.
- `POST /api/jobs/{id}/skip` → cancels the current account and moves on.
- `GET /api/template` → streams the Excel template.

### 10.2 WebSocket events (`/ws/jobs/{jobId}`)

All events share `{ type, jobId, email?, timestamp }`. `type` is one of:

- `RUN_STARTED { total }`
- `ACCOUNT_STARTED { email, index }`
- `STEP_STARTED { email, step }`
- `STEP_COMPLETED { email, step, durationMs }`
- `STEP_FAILED { email, step, error }`
- `AWAIT_MANUAL_LOGIN { email, timeoutSec }`
- `ACCOUNT_COMPLETED { email, status, resumeOldName?, resumeNewName? }`
- `RUN_COMPLETED { summary: { ok, authFailed, requiresManual, failed, skipped } }`
- `RUN_STOPPED`

---

## 11. Directory layout

```
F:\views\g\Naukri\
├── frontend/                 React + Vite + TypeScript + Tailwind + shadcn/ui
│   ├── src/
│   │   ├── theme/tokens.ts
│   │   ├── screens/{Setup,Run,Results}.tsx
│   │   ├── components/
│   │   └── api/{rest.ts,ws.ts}
│   ├── index.html
│   └── vite.config.ts
├── backend/                  Spring Boot Maven module
│   ├── src/main/java/com/adi/naukri/
│   │   ├── NaukriApplication.java
│   │   ├── api/{JobController,JobWebSocketHandler,TemplateController}.java
│   │   ├── automation/{NaukriAutomator,StepResult,PlaywrightSession}.java
│   │   ├── excel/EmailExcelParser.java
│   │   ├── report/{ReportWriter,RunSummary}.java
│   │   └── orchestrator/{JobOrchestrator,JobState}.java
│   └── pom.xml
├── electron/
│   ├── main.js
│   ├── preload.js
│   ├── electron-builder.yml
│   └── renderer/             (populated from frontend/dist during build)
├── build/
│   ├── build.ps1
│   └── fetch-jre.ps1
├── docs/superpowers/specs/
│   └── 2026-07-14-naukri-utility-design.md   ← this file
└── README.md
```

---

## 12. Build pipeline (`build/build.ps1`)

Single PowerShell script that orchestrates the end-to-end build:

1. `mvn -f backend/pom.xml clean package -DskipTests` → `backend/target/naukri-be.jar`.
2. `npm ci` and `npm run build` in `frontend/` → `frontend/dist/`.
3. Copy `frontend/dist/` → `electron/renderer/`.
4. Ensure portable Temurin JRE 17 (Windows x64) is present in `electron/resources/jre/` (download and cache if missing).
5. `playwright install chromium` into `electron/resources/playwright/`.
6. `npx electron-builder --win nsis portable` → `dist/NaukriAutomator-Setup-<ver>.exe` and `dist/NaukriAutomator-Portable-<ver>.exe`.

The script must be idempotent, print each phase clearly, and exit non-zero on any failed sub-step.

---

## 13. Non-goals (v1)

- No CAPTCHA solving. CAPTCHAs are treated as `REQUIRES_MANUAL`.
- No OTP / MFA handling in the automated path. (Manual-login mode is the escape hatch.)
- No persistent credential storage. Password is memory-only and cleared on window close.
- No parallel execution. Strictly one account at a time.
- No cloud, telemetry, or auto-update.
- No mobile / macOS / Linux packaging in v1.

---

## 14. Assumptions and risks

- Naukri's DOM selectors are the primary fragility surface. Selectors will live in a single `NaukriSelectors` constants class so DOM drift can be fixed in one place.
- Naukri may rate-limit or soft-block after many rapid logins from the same IP. Sequential execution mitigates but does not eliminate this. If it becomes a problem, a configurable inter-account delay (0–60 s) is a straightforward v1.1 addition.
- Chromium-only bundling is a deliberate size trade-off; if Naukri ships browser-specific bugs, we may need to add Firefox later.
- Manual-login mode assumes the user sees the browser window; using it with `headless = true` is nonsensical and will be prevented in the FE (the toggle disables headless).
