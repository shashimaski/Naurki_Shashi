# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Repo layout note

- `F:\views\g\Naukri` (this directory) — **working / build directory**. Contains full source, `dist/`, `build/`, `node_modules/`, generated JREs, Playwright cache, and iteration-only PowerShell helpers (`build-be-step.ps1`, `verify-*.ps1`, `smoke-*.ps1`, `run-smoke.ps1`, etc.).
- `F:\views\g\Naukri-Repo` — **commitable repo**. Same tree without local build artefacts / caches. When committing, mirror changes into `Naukri-Repo` and commit there.

## What this project is (one paragraph)

`NaukriAutomator` is a Windows desktop utility that refreshes the "last updated" timestamp on one or more Naukri.com profiles. Ships as a single ~397 MB `.exe` bundling Electron, a Spring Boot backend JAR, a portable JRE 17, and Playwright Chromium. The user provides a batch of Naukri emails (Excel upload or manual chips) plus a shared password; the backend automates each account sequentially and streams progress to the renderer over WebSocket.

## Per-account automation sequence (`NaukriAutomator.java`)

Steps are dispatched in this fixed order. The retry loop lives in `JobOrchestrator.processAccount` (whole-account retry, not per-step), driven by `RetryPolicy.attemptsFor(mode)`.

1. **LOGIN** — navigate `/nlogin/login`, type creds with human-like delays, or wait up to 5 min in manual-login mode (browser is forced visible; the `AWAIT_MANUAL_LOGIN` gate is published by the orchestrator's gate lambda, not the automator).
2. **DOWNLOAD_RESUME** — nav to profile via `.view-profile-wrapper a[href='/mnjuser/profile']`, click `[data-title='download-resume']` on the `#lazyAttachCV` card; captured via `Page.waitForResponse` matching `/cloudgateway-mynaukri/resman-aggregator-services/v\d+/users/self/profiles/<hash>/resume`.
3. **(internal) rename** — `ResumeRenamer` replaces any existing date pattern in the filename in place (`DD.MM.YYYY`, `DD-MM-YYYY`, `DD/MM/YYYY`, `YYYY-MM-DD`) preserving the separator; falls back to appending `_yyyy-MM-dd` if no date is found. Collisions get `-1`, `-2`, … suffix. Non-fatal; no `StepResult` is emitted for this.
4. **UPLOAD_RESUME** — set `input#attachCV[type='file']`, click `input.dummyUpload[value='Update resume']`, wait for shared `.success-message-container` toast.
5. **HEADLINE_APPEND** — open the headline modal (`.widgetHead .edit.icon` → `form[name='resumeHeadlineForm']` → `#resumeHeadlineTxt`), append a trailing dot/space (net-zero edit), submit, wait for toast.
6. **HEADLINE_STRIP** — reopen the modal and remove the appended character, submit, wait for toast.
7. **LOGOUT** — direct-nav `/nlogin/logout` (primary) with drawer + `a[data-type='logoutLink']` fallback; wipe cookies + storage; **explicitly close the Playwright browser context** at end of step.

All CSS/selectors are centralised in `NaukriSelectors.java` and every one traces back to `docs/real-naukri-dom-2026-07-16.md` (the ground-truth DOM reference — update it alongside any selector change).

## High-level architecture

```
Electron main (Node) ──spawn──► Java 17 + naukri-be.jar (Spring Boot, --server.port=0)
     │                                        │
     │ waitForPort() on stdout "NAUKRI_BE_PORT=<n>"
     │                                        │
     ▼                                        ▼
BrowserWindow?port=<n>            REST /api/*   WS /ws/jobs/{jobId}
     │  (preload injects                       │
     │   window.NAUKRI_BE_PORT)                │
     ▼                                        ▼
React renderer (Vite/TS/Tailwind)   JobOrchestrator (single-threaded worker)
   Setup → Run → Results                        │
                                                ▼
                                     NaukriAutomator ──► Playwright Chromium ──► naukri.com
```

- **`backend/` — Spring Boot 3, Java 17**
  - `com.adi.naukri.api` — REST controllers (`JobController`, `ExcelController`), `JobWebSocketHandler` for streaming, CORS + path-extracting interceptor.
  - `com.adi.naukri.automation` — `NaukriAutomator` (Playwright driver, per-account step sequence), `PlaywrightSession` (browser lifecycle; **caches browser but re-launches on `mode` change — headless vs headed**), `NaukriSelectors`, `ResumeRenamer`, `RetryPolicy`, `AutomatorConfig`, `StepListener`.
  - `com.adi.naukri.orchestrator` — `JobOrchestrator` runs accounts sequentially, publishes `JobEvent.*` (RunStarted/AccountStarted/StepStarted/StepCompleted/StepFailed/AwaitManualLogin/AccountCompleted/RunCompleted/RunStopped) to `JobEventBus` (100-event ring buffer per jobId). The WS handler replays the buffer to new subscribers then streams live. Concurrent job start returns HTTP 409 `{"error":"job-already-running"}` (via `@ExceptionHandler(IllegalStateException.class)`).
  - `com.adi.naukri.report` — `ReportWriter` writes `<outputFolder>/<runTimestamp>/{report.csv, report.json, logs/<email>.log, screenshots/<email>.png}` (screenshots only on FAILED).
- **`electron/`** — `main.js` spawns `resources/jre/bin/javaw.exe -jar resources/backend/naukri-be.jar --server.port=0`, extracts the port from stdout, and passes it via the window URL. `preload.js` exposes `window.NAUKRI_BE_PORT`, `window.electronAPI.{pickFolder, openFolder, portInfo}`, and (test-only) `window.__E2E_MOCK__`. `electron-builder.yml` packages JRE, backend JAR, and Playwright as `extraResources`.
- **`frontend/`** — Vite + React 18 + TS + Tailwind + Vitest. `App.tsx` is a three-state router: `setup → run → results`. API clients: `src/api/rest.ts` (REST) and `src/api/ws.ts` (WS at `ws://127.0.0.1:${port}/ws/jobs/${jobId}`); both fall back to port `5000` if `window.NAUKRI_BE_PORT` is absent (dev uses Vite proxy on :5173 → backend :8080).
- **`mock-naukri/`** — separate Spring Boot fat JAR that serves a DOM-equivalent Naukri stand-in for integration + E2E tests. Only bundled in `-Variant E2E` builds.

`AutomatorConfig` knobs: `baseUrl`, `downloadsDir`, `pageLoadMs`, `actionMs`, `postLoginActionMs` (25 s default — Naukri's Next.js hydration is slow), `manualLogin`, `manualLoginTimeout` (5 min), `initialDelayMs`.

## Common commands

Backend requires `JAVA_HOME` pointing at Temurin/Azul 17 (project uses `C:\Users\e182114\.jdks\azul-17.0.10`).

### Full build (produces installer + zip under `dist/`)

```powershell
.\build\build.ps1                 # Ship variant (default) — no mock JAR
.\build\build.ps1 -Variant E2E    # includes mock JAR under resources/mock/
```

Phases (in order): `fetch-jre` → `install-playwright` → `build-backend` → `build-frontend` → `build-mock` → `build-electron`. Each phase is idempotent.

### All tests

```powershell
.\build\test.ps1              # BE + Mock + FE + Electron + E2E
.\build\test.ps1 -SkipE2E     # everything except E2E
```

### Per-layer

```powershell
# Backend (unit + contract + integration, requires JAVA_HOME=17)
$env:JAVA_HOME = 'C:\Users\e182114\.jdks\azul-17.0.10'
mvn -f backend\pom.xml verify

# Single backend test class
mvn -f backend\pom.xml test -Dtest=ResumeRenamerTest

# Single backend test method
mvn -f backend\pom.xml test '-Dtest=ResumeRenamerTest#preservesExistingDatePattern'

# Mock Naukri server
mvn -f mock-naukri\pom.xml test

# Frontend (Vitest, all)
npm --prefix frontend run test:ci

# Single frontend test file
npm --prefix frontend run test -- src/screens/SetupScreen.test.tsx

# Electron tests
npm --prefix electron test

# E2E (requires a prior -Variant E2E build)
npm --prefix e2e install
npx --prefix e2e playwright install chromium
npm --prefix e2e test
```

### Dev loop

```powershell
.\dev.ps1                     # starts backend + Vite dev server (proxy /api and /ws to :8080)
```

### Backend-only hotpatch (fast iteration on a remote/installed target)

Once the app is installed on the target machine (via the NSIS installer at least once), backend-only changes can be shipped as a jar swap instead of a full 5–8 min rebuild.

```powershell
.\build\phases\build-backend-only.ps1
# or:
npm run build:be:hot
```

Produces `dist\hotpatch\naukri-be.jar` (~200 MB Spring Boot fat jar — bundles Playwright + POI + all deps — but builds in ~10 s vs the 5–8 min full pipeline). Copy that single file to the target's install dir, overwriting the existing jar:

```
%LOCALAPPDATA%\Programs\NaukriAutomator\resources\backend\naukri-be.jar
```

Relaunch the app. **Only valid for pure-Java changes** (automation, selectors, orchestrator, REST, retry, timeouts). Frontend / Electron main / preload changes still need `.\build\build.ps1`.

## Non-negotiables when editing

- **Any selector change must also update `docs/real-naukri-dom-2026-07-16.md`** — that doc is the ground truth for what the live Naukri DOM looks like. Selectors in `NaukriSelectors.java` and mock-naukri templates must stay in lockstep with it.
- **Mock and real must serve the same DOM shape.** After changing selectors, verify the mock still exposes the same hooks (`mock-naukri/src/main/resources/templates/*.html`) — the integration tests drive `NaukriAutomator` against the mock, so drift breaks IT.
- **`PlaywrightSession` re-launches on `mode` change.** Don't cache/reuse a browser across headed↔headless switches; the class already handles this via `lastMode` — don't work around it.
- **Screenshots go under the per-run subfolder**, not the raw output folder. `runDir` is computed once before the account loop in `JobOrchestrator` and passed as `downloadsDir` — preserve this.
- **`AWAIT_MANUAL_LOGIN` is published by the orchestrator's gate lambda only.** Don't add `listener.onManualLoginAwait` calls from `NaukriAutomator` (past regression — the FE countdown reset twice).
- **Concurrent `POST /api/jobs` must return 409**, not 500. The exception handler in `JobController` covers this — don't remove it.
- **Password is in-memory only.** Never write it to disk, logs, or reports.

## Documentation index

| File | Purpose |
|---|---|
| `docs/real-naukri-dom-2026-07-16.md` | Confirmed live-Naukri DOM reference — every selector traces here |
| `docs/superpowers/specs/2026-07-14-naukri-utility-design.md` | Approved design spec |
| `docs/superpowers/plans/2026-07-14-naukri-utility.md` | Implementation plan |
| `docs/testing.md` | Test-layer breakdown |
| `COMPLETION_SUMMARY.md` | Milestones, review outcomes, known-limitation follow-ups (M2–M5, G-1..G-8) |
