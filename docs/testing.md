# NaukriAutomator — Testing Guide

> **Author:** Adikarthik Gupta C B

This document describes every test layer in the project, how to run them individually, and how to inspect failures.

---

## Test Layers

### 1. Backend Unit + Contract + Integration Tests (BE verify)

**Framework:** JUnit 5, Spring Boot Test, MockMvc, StandardWebSocketClient  
**Location:** `backend/src/test/java/`  
**Coverage:** Parsers, renamer, retry policy, report writer, orchestrator state machine; REST endpoints (happy + error paths); WebSocket event stream; full Spring context integration with Mock Naukri.

```powershell
$env:JAVA_HOME = 'C:\Users\e182114\.jdks\azul-17.0.10'
mvn -f backend\pom.xml verify
```

**Inspect failures:** `backend\target\surefire-reports\*.txt` or `*.xml`.

---

### 2. Mock Naukri Tests

**Framework:** JUnit 5, Spring Boot Test, MockMvc  
**Location:** `mock-naukri/src/test/java/`  
**Coverage:** All mock routes (login, OTP, profile, resume, logout, state/reset endpoints).

```powershell
$env:JAVA_HOME = 'C:\Users\e182114\.jdks\azul-17.0.10'
mvn -f mock-naukri\pom.xml test
```

**Inspect failures:** `mock-naukri\target\surefire-reports\*.txt`.

---

### 3. Frontend Unit + Contract Tests (FE tests)

**Framework:** Vitest, React Testing Library, msw (Mock Service Worker)  
**Location:** `frontend/src/**/*.test.tsx`, `frontend/src/**/*.test.ts`  
**Coverage:** All React components and screens; API client (`rest.ts`, `ws.ts`); form validation; WebSocket event handling.

```powershell
npm --prefix frontend run test:ci
```

**Inspect failures:** Vitest outputs inline in the terminal. For verbose output:

```powershell
npm --prefix frontend run test -- --reporter=verbose
```

---

### 4. Electron Tests

**Framework:** Jest (or Vitest in Electron)  
**Location:** `electron/test/`  
**Coverage:** `parsePortLine`, `waitForPort` helpers in `src/ipc.js`.

```powershell
npm --prefix electron test
```

**Inspect failures:** Output inline in the terminal.

---

### 5. Full-Stack E2E Tests

**Framework:** Playwright Test  
**Location:** `e2e/tests/`  
**Specs:**
- `happy-path.spec.ts` — two accounts → results-screen with count-ok = 2, report.csv present.
- `manual-login.spec.ts` — OTP account, manual-login toggle, callout, continue → count-ok = 1.
- `excel-upload.spec.ts` — XLSX with 3 emails → results-screen with count-ok = 3.

**Prerequisites:**
1. `mock-naukri/target/mock-naukri.jar` must exist (run `mvn -f mock-naukri\pom.xml package`).
2. Build the E2E variant of the Electron app: `.\build\build.ps1 -Variant E2E`.
3. Install e2e dependencies and Playwright browser:

```powershell
npm --prefix e2e install
npx --prefix e2e playwright install chromium
```

**Run E2E tests:**

```powershell
npm --prefix e2e test
```

**Inspect failures:**
- HTML report: `e2e\playwright-report\index.html` (open in browser).
- Screenshots and traces: `e2e\test-results\`.
- Run a single spec in headed mode:

```powershell
npx --prefix e2e playwright test tests\happy-path.spec.ts --headed
```

---

## Single Command — All Layers

Run all test layers (BE + Mock + FE + Electron + E2E):

```powershell
.\build\test.ps1
```

Skip E2E (useful when JRE/Playwright binary download is unavailable):

```powershell
.\build\test.ps1 -SkipE2E
```

The script exits 0 (`ALL GREEN`) or 1 (`FAILED: <section list>`).

---

## Common Failure Patterns

| Symptom | Likely cause | Fix |
|---|---|---|
| BE `JAVA_HOME` not found | `JAVA_HOME` env var not set | `$env:JAVA_HOME = 'C:\Users\e182114\.jdks\azul-17.0.10'` |
| FE `test:ci` hangs | Missing `vitest --run` flag | Ensure `test:ci` script uses `--run` |
| E2E `java not found` | Java not on `PATH` or JAR missing | Build mock jar; ensure Java 17 is on PATH |
| E2E Playwright `browserType.launch` error | Chromium not installed | `npx --prefix e2e playwright install chromium` |
| E2E results-screen timeout | Mock jar slow to start | Increase `waitForMock` timeout or check mock port |
| `report.csv` not found | Output folder path mismatch | Orchestrator writes to `<out>/<timestamp>/report.csv`; use recursive scan |

---

*Built by Adikarthik Gupta C B*
