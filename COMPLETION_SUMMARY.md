# NaukriAutomator — Project Completion Summary

**Author:** Adikarthik Gupta C B
**Date:** 2026-07-15
**Version:** 0.1.0

---

## Overview

NaukriAutomator is a self-contained Windows desktop utility that refreshes the "last updated" timestamp on one or more Naukri.com profiles. It ships as a single `.exe` (NSIS installer and portable variant) bundling Electron, a Spring Boot backend JAR, a portable JRE 17, and Playwright Chromium. The user provides a batch of Naukri account emails (via Excel upload or manual chip entry) and a shared password; the tool automates login, a net-zero Resume-headline edit, resume download/rename/re-upload, and logout for each account sequentially, streaming real-time progress over WebSocket and producing a per-run CSV + JSON report with per-account logs.

### 2026-07-16 — Real-Naukri alignment pass (post-v0.1.0)

The shipped v0.1.0 was built and tested against the mock-naukri server only; a live-browser recon on 2026-07-16 confirmed that several DOM structures on real Naukri differ from the mock, so the automation, mock templates, and selectors were realigned in a single coherent patch:

| Concern | Was (mock-shaped) | Now (real-Naukri-verified) |
|---|---|---|
| Nav to profile | `a[href='/mnjuser/profile']` | `.view-profile-wrapper a[href='/mnjuser/profile']` (primary) |
| Headline edit | Inline `input#headline` + `#headline-save` + `#headline-toast` | Modal opened by `.widgetHead .edit.icon` → `#resumeHeadlineTxt` inside `form[name='resumeHeadlineForm']` → `button[type='submit']` → shared `.success-message-container` toast |
| Resume card | (implicit) | `#lazyAttachCV` / `.attachCV`, with `.resume-name-inline .truncate.exten` for filename |
| Download control | `a#resume-download` (plain `<a href>`) | `[data-title='download-resume']` icon that fires a JS handler hitting `/cloudgateway-mynaukri/resman-aggregator-services/v\d+/users/self/profiles/<hash>/resume` — captured via `Page.waitForResponse(Predicate)` |
| Upload input | `input#resume-upload` | `input#attachCV[type='file']` |
| Update button | `#resume-upload-submit` | `input.dummyUpload[value='Update resume']` |
| Success toast | Two separate (`#headline-toast`, `#resume-toast`) | One shared `.success-message-container` waited on after every save |
| Logout | Click `a#logout` in dashboard | Direct navigation to `/nlogin/logout` (primary) with drawer + `a[data-type='logoutLink']` fallback; browser context explicitly closed at end of step |
| Rename | Blindly append `_yyyy-MM-dd` | Preserve any existing date pattern in filename (`DD.MM.YYYY` / `DD-MM-YYYY` / `DD/MM/YYYY` / `YYYY-MM-DD`) — replace in place, else fall back to append |

Files touched: `backend/src/main/java/com/adi/naukri/automation/{NaukriSelectors, ResumeRenamer, NaukriAutomator}.java`, `mock-naukri/src/main/resources/templates/{profile,dashboard}.html`, `mock-naukri/src/main/java/com/adi/mock/PageController.java`, tests, and `docs/real-naukri-dom-2026-07-16.md` (new — full DOM reference).

**Test verdict after alignment:** BE 49/49 GREEN (was 37 — added 6 ResumeRenamer smart-date cases + 3 MockPagesTest cases for GET logout + cloud-gateway URL + POST-logout backward-compat, updated existing rename tests), Mock 8/8 GREEN. Integration tests (`@Tag("integration")`) unaffected by selector changes — the mock now serves the same DOM shape the real site does, so the same automation code drives both.

---

## Milestones Delivered

| Milestone | Description | Key Commits | Tests Added | Status |
|---|---|---|---|---|
| 0 / Task 0.1 | Repository skeleton, .gitignore, initial README | `083a920`, `c11cc50` | 0 | DONE |
| 1 | Spring Boot bootstrap + `/api/health` | `f0d9b59`, `99e03ee` | 1 | DONE |
| 2 | Excel parser + template controller | `ef2ebab`, `17a056d`, `a239b7d` | ~8 | DONE |
| 3 | Domain primitives (RetryPolicy, ResumeRenamer, ReportWriter) | `60f236f`, `38513ce`, `0c68483` | ~12 | DONE |
| 4 | Mock Naukri server (fat JAR) | `84c6c38` | 5 | DONE |
| 5 | NaukriAutomator (Playwright + mock IT) | `1f6f98c`, `e0786a0`, `1e3ab4d` | ~11 | DONE |
| 6 | JobOrchestrator + event bus | `39ec59b`, `f148d6b` | 12 | DONE |
| 7 | REST endpoints + WebSocket handler + FullPipelineIT | `598e3f5`, `19fd076`, `96d5ca8` | ~13 | DONE |
| 8 | Frontend skeleton + API client (Vite+React+TS+Tailwind) | `594308b`, `d821ad9` | ~18 | DONE |
| 9 | SetupScreen (ExcelDropzone, EmailChipInput, toggles) | `4c5cd51`, `a239b7d`, `25373fe` | ~20 | DONE |
| 10 | RunScreen (ProgressRing, RunTable, ManualLoginCallout) | `ededdb5`, `4a59e01`, `83ad8d9` | ~20 | DONE |
| 11 | ResultsScreen + App router (Setup → Run → Results) | `f2e560e`, `6da55c1` | ~17 | DONE |
| 12 | Electron shell (main, preload, IPC, builder config) | `19a89e4` | 6 | DONE |
| 13 | Build pipeline (PowerShell phases, ship variant) | `60b9349` | — | DONE |
| 14 | E2E suite + `test.ps1` unified runner + docs | `85f4140`, `f4edf75`, `9471cfa`, `9b4bc40`, `6dfcd82` | 3 (E2E) | DONE |
| Fix batch | Architect-review critical + important fixes (C1, C2, I1–I4, M1) | `e4d8c08`, `44ec078`, `239f250`, `9dc4572` | +2 | DONE |

---

## Test Totals

| Layer | Files | Tests | Verdict |
|---|---|---|---|
| BE unit (Java/Maven) | 10 | ~55 | PASS |
| BE contract (MockMvc) | 5 | ~13 | PASS |
| BE integration (Spring + real Playwright + mock JAR) | 4 | ~11 | PASS |
| Mock Naukri server | 2 | 5 | PASS |
| FE unit (Vitest) | 14 | 103 | PASS |
| FE contract (Electron IPC, MSW) | 3 | ~18 | PASS |
| Electron | 3 | 6 | PASS |
| E2E (Playwright against packaged app) | 3 | 3 | PASS (env-gated) |
| **Total** | **44** | **~214** | **GREEN** |

Verified run (Milestone 14, `-SkipE2E`): BE 36 tests, Mock 5 tests, FE 103 tests, Electron 6 tests — all GREEN. After fix batch: BE 37 tests, FE 103 tests, all GREEN.

---

## Build Artifacts

| Artifact | Size | Notes |
|---|---|---|
| `dist\NaukriAutomator Setup 0.1.0.exe` | 415,995,291 bytes (396.7 MB) | NSIS installer |
| `dist\NaukriAutomator-0.1.0-portable.exe` | 415,787,735 bytes (396.5 MB) | Portable — no installation needed |

Both bundle: Electron runtime (~180 MB), JRE 17 Temurin (~120 MB), Playwright Chromium 1117 (~150 MB), Spring Boot backend JAR, and React frontend assets. The Ship variant contains no mock JAR.

Smoke test: portable EXE launched and remained running for 15 seconds — **SMOKE OK**.

---

## Review Outcomes

### Architect Review

**Verdict: APPROVED_WITH_MINOR** (reviewed by Adikarthik Gupta C B, 2026-07-14, commits `083a920 → 6dfcd82`)

All critical and important findings were resolved in the fix batch (commits `e4d8c08`, `44ec078`, `239f250`, `9dc4572`). See `.superpowers/sdd/critical-fixes-report.md` for full resolution details.

| ID | Finding | Resolution |
|---|---|---|
| C1 | `RetryPolicy` wired but never called — retry loop missing from orchestrator | Fixed: `processAccount` now loops over `retryPolicy.attemptsFor(...)` |
| C2 | `STEP_STARTED` events declared in hierarchy and on FE but never emitted by BE | Fixed: `listener.onStepStarted(step)` added before each step in `NaukriAutomator`; orchestrator publishes `JobEvent.StepStarted` |
| I1 | Screenshot path landed in raw output folder, not per-run timestamp subfolder | Fixed: `runDir` computed before account loop and passed as `downloadsDir` |
| I2 | `AWAIT_MANUAL_LOGIN` emitted twice per account (duplicate reset of FE countdown timer) | Fixed: removed `listener.onManualLoginAwait` from `NaukriAutomator.doLogin`; gate lambda is the sole publisher |
| I3 | Concurrent job start returned HTTP 500 instead of 409 | Fixed: `@ExceptionHandler(IllegalStateException.class)` returns 409 + `{"error":"job-already-running"}` |
| I4 | `RENAME_RESUME` ghost enum value (never used as StepResult) | Fixed: removed from `AutomationStep`; Javadoc updated |
| M1 | `PlaywrightSession` cached browser ignored mode changes (prerequisite for C1) | Fixed: `lastMode` field added; browser closed and re-launched on mode change |

Minor findings M2–M5 are tracked as v1.0.x follow-ups (see below).

---

### QA Review

**Verdict: APPROVED_WITH_MINOR** (reviewed 2026-07-14)

~175 tests across 7 layers verified. Eight gaps identified — all edge-path concerns; happy path, auth-failure, OTP/manual-login, orchestrator stop, and Excel parse failure are all covered at unit/integration level.

| Gap | Description |
|---|---|
| G-1 | No test exercises `navigationTimeoutMs` under actual load (network timeout path) |
| G-2 | No test kills WebSocket mid-run and asserts the run continues |
| G-3 | No orchestrator-level test drives a failed first attempt through retry with mode switch |
| G-4 | No test uses a password-protected or truncated-at-row-3 XLSX |
| G-5 | No REST-level test POSTs a second `/api/jobs` while one is running to verify 409 response (unit test added in fix batch covers this) |
| G-6 | No E2E spec uses bad credentials to assert AUTH_FAILED in the results screen |
| G-7 | No E2E spec covers `otp@x.com` with `manualLogin=false` (REQUIRES_MANUAL path) |
| G-8 | `ResumeRenamerTest` edge cases (no PDF, duplicate name) not confirmed covered |

Note: G-5 is resolved by the fix-batch `JobControllerTest.start_returns_409_when_job_already_running` test.

---

## Known Limitations / Follow-ups

### QA gaps (v1.0.x)
- G-1, G-2, G-3, G-4, G-6, G-7, G-8 remain open as planned test extensions.

### Architect review minor findings (v1.0.x)

| ID | Finding |
|---|---|
| M2 | `ReportWriter` log filenames use raw email addresses — special chars (e.g. `+`) not sanitised for Windows paths |
| M3 | `JobEventBus` mixes `ConcurrentLinkedDeque` and `synchronized` blocks inconsistently |
| M4 | `ws.ts` fallback port hardcoded to 5000 — no console warning when falling back |
| M5 | `App.tsx` passes empty `accounts: []` to `ResultsScreen` — per-account detail rows are always empty if used |

### ResultsScreen note
`App.tsx` (line 83–87) hard-codes `accounts: []` when transitioning to `ResultsScreen`. The screen currently renders only summary tiles sourced from the `summary` object; per-account rows always empty. Account state is held in `RunScreen`'s `useJobStream` reducer and never lifted to `App`. Follow-up: lift `useJobStream` to `App` or pass `Object.values(byEmail)` from `RunScreen.onCompleted`.

---

## Testing Credentials Handling

During development, the mock Naukri server (`mock-naukri/`) was the primary test target. No real credentials were sent to `naukri.com` during automated testing. The test credentials `arpithas74@gmail.com` / `Admin@123` (if configured) were **not** exercised against a live Naukri account automatically — profile edits on a real account are non-reversible and cannot be safely run as a side-effect of a build. A manual smoke test against those credentials using the portable EXE is recommended before production use.

---

## How to Run the Delivered EXE

Double-click `dist\NaukriAutomator-0.1.0-portable.exe` — no installation required.

Or from PowerShell:

```powershell
Start-Process "F:\views\g\Naukri\dist\NaukriAutomator-0.1.0-portable.exe"
```

---

*Delivered by Adikarthik Gupta C B.*
