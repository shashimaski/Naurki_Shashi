# NaukriAutomator

Windows desktop utility that refreshes the "last updated" date on one or more Naukri.com profiles by automating login → net-zero Resume-headline edit → resume rename + re-upload → **logout + browser close**.

**Author:** Adikarthik Gupta C B
**Version:** 0.1.0

> **2026-07-16 alignment pass.** Selectors + flow shape re-verified against a live Naukri browser session. Highlights: modal-based Resume-headline edit (`#resumeHeadlineTxt` inside `form[name='resumeHeadlineForm']`), resume card at `#lazyAttachCV` with `[data-title='download-resume']` icon, sniffed download URL under `/cloudgateway-mynaukri/resman-aggregator-services/v1/users/self/profiles/<hash>/resume`, single shared `.success-message-container` toast waited on after every save, direct-nav logout to `/nlogin/logout` with drawer-based `[data-type='logoutLink']` fallback, and browser context explicitly closed at end of LOGOUT step. Smart resume-rename preserves existing date patterns in the filename (e.g. `Arpitha S 15.07.2026 yahoo.pdf` → `Arpitha S 16.07.2026 yahoo.pdf`). Mock-naukri templates rewritten to mirror. See `docs/real-naukri-dom-2026-07-16.md`.

---

## Features

- Batch of Naukri accounts entered as (Name, Email) pairs — via Excel upload (`Name` / `Email` / `Remarks` columns, case-insensitive headers) OR the manual chip input
- One common password for the batch (in-memory only; never persisted or logged)
- **Local-folder resume flow** — point at a folder of per-account resume files named `<Name>*.pdf`; the automator locates each account's file by name, smart-renames it in place with today's date, and uploads. No downloading from Naukri required.
- Toggle: run browser visibly (default) or headless
- Toggle: log in manually per account (auto-detects dashboard, 5-min timeout)
- Per-account live progress over WebSocket, retry policy, and a per-run report (`inputs.json` + `report.json` + `report.csv` + per-account logs)

---

## Prerequisites (for building from source)

The build pulls a JRE + Chromium on first run, so the toolchain below is all you need locally.

| Requirement | Version | Where used | Verify |
|---|---|---|---|
| Windows | 10 / 11 (x64) | host OS | `winver` |
| Java JDK | **17** (Temurin or Azul) — project default path: `C:\Users\e182114\.jdks\azul-17.0.10` | backend + mock (`mvn`), auto-set by build scripts via `JAVA_HOME` | `java -version` |
| Maven | 3.9+ | backend + mock package phase | `mvn -v` |
| Node.js | **20 LTS** | frontend + electron-builder + e2e | `node -v` |
| npm | bundled with Node 20 (≥ 10) | Vite build, electron-builder, npx | `npm -v` |
| PowerShell | 5.1+ (default on Windows 10/11) | build/test scripts | `powershell -Command "$PSVersionTable.PSVersion"` |
| Git | any recent | source checkout | `git --version` |
| Internet access | first build only | downloads JRE + Playwright Chromium | — |
| Free disk space | ~3 GB | `dist\`, `node_modules\`, `~\.m2\repository\`, bundled JRE + Chromium under `electron\resources\` | — |
| `%LOCALAPPDATA%` writable | yes | NSIS installs per-user under `%LOCALAPPDATA%\Programs\NaukriAutomator\` (no admin needed) | — |

**Environment:** the build scripts set `JAVA_HOME` themselves — you don't need to add it to your user env, but if you build outside the scripts (e.g. running `mvn` directly) set `JAVA_HOME` to a JDK 17 install.

**PowerShell execution policy:** the `.bat` wrappers below invoke PowerShell with `-ExecutionPolicy Bypass`, so you do **not** need to relax your machine's global policy. Running the underlying `.ps1` files directly will fail unless you've allowed script execution.

---

## Build

The easiest entry points are the `.bat` wrappers at the repo root — double-click them from Explorer or run them from `cmd` / PowerShell.

| Batch file | What it does | Underlying script |
|---|---|---|
| **`build.bat`** | Full app build → `dist\NaukriAutomator Setup 0.1.0.exe` (NSIS) + `dist\NaukriAutomator 0.1.0.exe` (portable). Takes ~5–8 min on first run, ~2–3 min once JRE + Chromium are cached. | `build\build.ps1` |
| **`build-be-hot.bat`** | Backend-only hotpatch → `dist\hotpatch\naukri-be.jar` (~203 MB, ~10 s). Copy that single jar over `%LOCALAPPDATA%\Programs\NaukriAutomator\resources\backend\naukri-be.jar` on an already-installed target and relaunch. **Only for pure-Java changes** — frontend/Electron changes need the full `build.bat`. | `build\phases\build-backend-only.ps1` |
| **`test.bat`** | Full test suite: BE + Mock + FE + Electron + E2E. Add `-SkipE2E` to skip the packaged-app E2E layer. | `build\test.ps1` |

Both `.bat` files forward extra arguments to the underlying PowerShell script:

```
build.bat                   ::  Ship variant (default installer + portable)
build.bat -Variant E2E      ::  includes the mock JAR for E2E tests
test.bat -SkipE2E           ::  everything except E2E
```

If you prefer PowerShell directly:

```powershell
.\build\build.ps1                 # Ship variant
.\build\build.ps1 -Variant E2E    # includes mock JAR under resources/mock/
```

Outputs (under `dist\`):

- `NaukriAutomator Setup 0.1.0.exe` — NSIS installer (~396 MB)
- `NaukriAutomator 0.1.0.exe` — portable exe (~396 MB, self-extracts on each launch — slower cold start; prefer the installer for daily use)

The pipeline downloads a bundled JRE 17 on first run, installs Playwright Chromium, packages the Spring Boot backend JAR and React frontend, then invokes electron-builder. Each phase is idempotent — reruns skip anything already cached under `electron\resources\`.

### Which one to use

Both bundle the exact same app; the difference is only how it lands on disk:

| Distribution | Best for | One-time cost | Every launch |
|---|---|---|---|
| **NSIS installer** | Users who want a Start-menu + Desktop shortcut | ~22 s per-user install (no admin) | **~12 s** to first window |
| **Portable exe** | USB stick / no install rights | none | 60–120 s (re-extracts 397 MB to `%TEMP%` on each launch) |

The installer keeps the extracted files under `%LOCALAPPDATA%\Programs\NaukriAutomator\` for the JVM cold-start floor of ~12 s; the portable re-extracts on every launch, so use the installer for anything but throwaway runs.

---

## Run tests

```
test.bat                    ::  BE + Mock + FE + Electron + E2E
test.bat -SkipE2E           ::  everything except E2E
```

Or directly:

```powershell
.\build\test.ps1
.\build\test.ps1 -SkipE2E
```

Individual layers:

```powershell
# Backend (Java 17 required)
$env:JAVA_HOME = 'C:\Users\e182114\.jdks\azul-17.0.10'
mvn -f backend\pom.xml verify

# Mock Naukri server
mvn -f mock-naukri\pom.xml test

# Frontend (Vitest)
npm --prefix frontend run test:ci

# Electron
npm --prefix electron test

# E2E (requires prior -Variant E2E build)
npm --prefix e2e install
npx --prefix e2e playwright install chromium
npm --prefix e2e test
```

---

## Reports layout

For each run the app writes under `<outputFolder>\<runTimestamp>\`:

| File | Contents |
|---|---|
| `inputs.json` | Snapshot of the inputs that started the run: `jobId`, `runStartedAt`, `accounts[] {name,email}`, `headless`, `manualLogin`, `outputFolder`, `resumeFolderPath`, `baseUrlOverride`, `initialDelayMs`, `passwordProvided` (boolean flag — the password value is **never** persisted). |
| `report.json` | `{ "inputs": {...}, "accounts": [...] }` — self-contained: same `inputs` snapshot as `inputs.json`, plus per-account results with step timings. |
| `report.csv` | Per-account rows: email, status, error, resume_old_name, resume_new_name, startedAt, endedAt, retries. |
| `logs\<email>.log` | Plain-text per-account log. |
| `screenshots\*.png`, `dom-dumps\*.html` | Only when a step fails — one screenshot + one DOM dump per failed step. |

---

## Manual login mode

Enable the toggle on the Setup screen. The browser opens; you complete login (including OTP or CAPTCHA). The app auto-detects the dashboard URL and resumes automation.

- **Continue now** — resumes automation immediately after you have logged in.
- **Skip this account** — marks the account as SKIPPED and moves on.
- **Timeout:** 5 minutes. An account that times out is marked `REQUIRES_MANUAL` in the report.

Manual-login mode forces the browser to run visibly; the headless toggle is ignored for affected accounts.

---

## Non-goals (v0.1.0)

- No CAPTCHA solving
- No automated OTP handling — use Manual login mode instead
- No credential persistence (password is in-memory only)
- No parallel accounts (sequential only)
- No cloud sync, telemetry, or auto-update

---

## Architecture (short)

Electron main → spawns bundled Java 17 + `naukri-be.jar` as a child process on a random localhost port → REST + WebSocket → Playwright Chromium (bundled). The renderer communicates with the backend exclusively over `http://127.0.0.1:<port>`. No traffic leaves the machine except to `naukri.com` via the Playwright browser.

See `docs/superpowers/specs/2026-07-14-naukri-utility-design.md` for the full design spec.

---

## Documentation

| File | Purpose |
|---|---|
| `docs/superpowers/specs/2026-07-14-naukri-utility-design.md` | Approved design spec |
| `docs/superpowers/plans/2026-07-14-naukri-utility.md` | Implementation plan |
| `docs/testing.md` | Test-layer breakdown |
| `docs/real-naukri-dom-2026-07-16.md` | Confirmed live-Naukri DOM reference (every selector traces back to a fragment here) |
| `COMPLETION_SUMMARY.md` | Project completion summary and review outcomes |

---

*Built by Adikarthik Gupta C B*
