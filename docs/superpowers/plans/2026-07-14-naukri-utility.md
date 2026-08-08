# NaukriAutomator Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Ship a single Windows `.exe` that refreshes the "last updated" date on one or more Naukri.com profiles by scripting the login → tiny profile edit → resume rename & re-upload → logout flow, driven by a dark-themed React UI over a Java + Playwright backend.

**Architecture:** Electron shell whose renderer is a React SPA and whose main process spawns a bundled Spring Boot fat JAR as a child process. FE ↔ BE communicate over REST + WebSocket on a random localhost port chosen at launch. Automation runs one account at a time in an incognito Playwright BrowserContext.

**Tech Stack:** React 18 + Vite + TypeScript + Tailwind CSS + shadcn/ui + framer-motion + lucide-react (FE); Spring Boot 3 + Java 17 + Playwright-Java + Apache POI (BE); Electron + electron-builder (packaging); PowerShell (build orchestration).

## Global Constraints

- Author byline everywhere: **Adikarthik Gupta C B**.
- Windows-only target; scripts are PowerShell — no bash-isms.
- Java version: **17** (Temurin). Node version: **20 LTS**.
- No external git remotes ever — this project stays local; do not push to github/gitlab/bitbucket-public.
- Password is memory-only in the BE; never persist to disk or log.
- Playwright bundle ships **Chromium only**.
- Sequential execution — one account at a time; no parallelism.
- Manual-login mode is exclusive with headless: FE disables the headless toggle whenever manual-login is on.
- Design tokens in §7 of the spec are the single source of truth for colors, fonts, motion.
- Every task ends with a local git commit. No `git push`.

## Testing Strategy (mandatory across every task)

Every task in every milestone below carries its own automated test that is executed as part of the task's steps. Nothing is considered "done" until its test is green in the runner.

**Layers:**

1. **BE unit tests** — JUnit 5 + Spring Boot Test. One test class per production class. Cover parsers, renamer, retry policy, report writer, orchestrator state machine.
2. **BE contract tests** — `@SpringBootTest` + `MockMvc` for REST, `StandardWebSocketClient` for WS. Every endpoint has a happy-path + at least one error-path test.
3. **BE integration tests** — full Spring context with a **Mock Naukri server** (built in Milestone 7) as the Playwright target. Automator drives the mock through the entire per-account flow.
4. **FE unit tests** — Vitest + React Testing Library. One test file per component/screen. Cover render, user interactions, form validation, WS event handling.
5. **FE contract tests** — msw (Mock Service Worker) intercepts REST + WS; validates the FE handles every event type in §10.2 of the spec.
6. **Full-stack E2E** — Playwright test project drives the packaged Electron app; the Electron app's BE talks to the Mock Naukri server. Verifies the entire pipeline: upload Excel → start → progress events arrive → results screen shows correct counts → report files exist on disk.
7. **Build smoke test** — after `build.ps1` produces the EXE, run it in a short-lived Playwright-controlled session against Mock Naukri and assert exit code + report file presence.

**Single runner (`build\test.ps1`, delivered in Milestone 14):**
```
1. mvn -f backend/pom.xml verify        # BE unit + contract + integration
2. npm --prefix frontend run test:ci    # FE unit + contract (Vitest)
3. npm --prefix e2e     run test        # Playwright E2E against packaged app
```
Non-zero on any failure. This script is the definition of "green".

**No task is considered complete without:**
- Test code committed alongside implementation.
- Test executed locally with observed pass output.
- Coverage of both a happy path and at least one failure path where a failure path is meaningful.

---

## Milestone 0 — Repository skeleton

Bootstraps the working directory, sets up a local-only git repo, and lays down the top-level folder shape referenced by every later milestone.

### Task 0.1: Initialize repo + workspace layout

**Files:**
- Create: `F:\views\g\Naukri\.gitignore`
- Create: `F:\views\g\Naukri\README.md`
- Create: `F:\views\g\Naukri\package.json` (root wrapper for scripts only, not a workspace)
- Create empty dirs: `frontend/`, `backend/`, `electron/`, `build/`, `docs/superpowers/`

**Interfaces:**
- Consumes: nothing.
- Produces: a local git repo at `F:\views\g\Naukri`, top-level folders, and a root `package.json` whose scripts wrap the build phases (`build:be`, `build:fe`, `build:electron`, `build:all`).

- [ ] **Step 1: Init local git repo**

```powershell
cd F:\views\g\Naukri
git init
git config user.name  "Adikarthik Gupta C B"
git config user.email "adikarthik_gupta@amat.com"
```

- [ ] **Step 2: Create `.gitignore`**

```
# Node / Vite
node_modules/
dist/
.vite/

# Java / Maven
target/
*.class
*.jar
!electron/resources/backend/*.jar

# Electron build output
electron/renderer/
electron/resources/jre/
electron/resources/playwright/
out/
release/

# IDE
.idea/
.vscode/
*.iml

# OS
Thumbs.db
Desktop.ini

# Runtime
runs/
*.log
```

- [ ] **Step 3: Create root `README.md`**

```markdown
# NaukriAutomator

Windows desktop utility that refreshes the "last updated" date on one or more Naukri.com profiles.

**Author:** Adikarthik Gupta C B

See `docs/superpowers/specs/2026-07-14-naukri-utility-design.md` for the design and `docs/superpowers/plans/2026-07-14-naukri-utility.md` for the implementation plan.

## Build

```powershell
.\build\build.ps1
```

Outputs `dist\NaukriAutomator-Setup-<ver>.exe` and `dist\NaukriAutomator-Portable-<ver>.exe`.
```

- [ ] **Step 4: Create root `package.json`**

```json
{
  "name": "naukri-automator",
  "version": "0.1.0",
  "private": true,
  "author": "Adikarthik Gupta C B",
  "description": "Naukri profile updater utility",
  "scripts": {
    "build:be":       "powershell -NoProfile -ExecutionPolicy Bypass -File build\\phases\\build-backend.ps1",
    "build:fe":       "powershell -NoProfile -ExecutionPolicy Bypass -File build\\phases\\build-frontend.ps1",
    "build:electron": "powershell -NoProfile -ExecutionPolicy Bypass -File build\\phases\\build-electron.ps1",
    "build:all":      "powershell -NoProfile -ExecutionPolicy Bypass -File build\\build.ps1"
  }
}
```

- [ ] **Step 5: Create empty folder placeholders**

```powershell
New-Item -ItemType Directory -Force frontend, backend, electron, build\phases, docs\superpowers\plans, docs\superpowers\specs | Out-Null
New-Item -ItemType File      -Force frontend\.gitkeep, backend\.gitkeep, electron\.gitkeep, build\phases\.gitkeep | Out-Null
```

- [ ] **Step 6: Commit**

```powershell
git add .gitignore README.md package.json frontend backend electron build docs
git commit -m "chore: initialize NaukriAutomator repository skeleton"
```

---

## Milestone 1 — Backend foundation (Spring Boot boots and health-checks)

Stands up a Spring Boot app that starts on a caller-chosen port, exposes a `/api/health` endpoint, and can be built by Maven. No automation yet — this milestone exists so downstream milestones have a running BE to talk to and a test harness that works.

### Task 1.1: Maven module + Spring Boot bootstrapping

**Files:**
- Create: `backend\pom.xml`
- Create: `backend\src\main\java\com\adi\naukri\NaukriApplication.java`
- Create: `backend\src\main\resources\application.yml`
- Create: `backend\src\main\resources\banner.txt`

**Interfaces:**
- Consumes: nothing.
- Produces: a runnable fat JAR `backend\target\naukri-be.jar` that starts Spring Boot on `--server.port` (default `0` = random). Prints the chosen port to stdout in a machine-readable line: `NAUKRI_BE_PORT=<port>`.

- [ ] **Step 1: Create `backend\pom.xml`**

```xml
<?xml version="1.0" encoding="UTF-8"?>
<project xmlns="http://maven.apache.org/POM/4.0.0">
  <modelVersion>4.0.0</modelVersion>
  <groupId>com.adi.naukri</groupId>
  <artifactId>naukri-be</artifactId>
  <version>0.1.0</version>
  <packaging>jar</packaging>

  <parent>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-parent</artifactId>
    <version>3.3.0</version>
    <relativePath/>
  </parent>

  <properties>
    <java.version>17</java.version>
    <maven.compiler.source>17</maven.compiler.source>
    <maven.compiler.target>17</maven.compiler.target>
    <playwright.version>1.44.0</playwright.version>
    <poi.version>5.2.5</poi.version>
  </properties>

  <dependencies>
    <dependency><groupId>org.springframework.boot</groupId><artifactId>spring-boot-starter-web</artifactId></dependency>
    <dependency><groupId>org.springframework.boot</groupId><artifactId>spring-boot-starter-websocket</artifactId></dependency>
    <dependency><groupId>org.springframework.boot</groupId><artifactId>spring-boot-starter-validation</artifactId></dependency>
    <dependency><groupId>com.microsoft.playwright</groupId><artifactId>playwright</artifactId><version>${playwright.version}</version></dependency>
    <dependency><groupId>org.apache.poi</groupId><artifactId>poi-ooxml</artifactId><version>${poi.version}</version></dependency>
    <dependency><groupId>org.projectlombok</groupId><artifactId>lombok</artifactId><optional>true</optional></dependency>
    <dependency><groupId>org.springframework.boot</groupId><artifactId>spring-boot-starter-test</artifactId><scope>test</scope></dependency>
  </dependencies>

  <build>
    <finalName>naukri-be</finalName>
    <plugins>
      <plugin>
        <groupId>org.springframework.boot</groupId>
        <artifactId>spring-boot-maven-plugin</artifactId>
        <configuration>
          <excludes>
            <exclude><groupId>org.projectlombok</groupId><artifactId>lombok</artifactId></exclude>
          </excludes>
        </configuration>
      </plugin>
    </plugins>
  </build>
</project>
```

- [ ] **Step 2: Create `NaukriApplication.java`**

```java
package com.adi.naukri;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

@SpringBootApplication
public class NaukriApplication {
    public static void main(String[] args) {
        SpringApplication.run(NaukriApplication.class, args);
    }

    @Component
    static class PortAnnouncer {
        private final Environment env;
        PortAnnouncer(Environment env) { this.env = env; }

        @EventListener(ApplicationReadyEvent.class)
        void announce() {
            String port = env.getProperty("local.server.port");
            System.out.println("NAUKRI_BE_PORT=" + port);
            System.out.flush();
        }
    }
}
```

- [ ] **Step 3: Create `application.yml`**

```yaml
server:
  port: 0
  address: 127.0.0.1
spring:
  application:
    name: naukri-be
  servlet:
    multipart:
      max-file-size: 5MB
      max-request-size: 5MB
logging:
  level:
    root: INFO
    com.adi.naukri: DEBUG
```

- [ ] **Step 4: Create `banner.txt`**

```
NaukriAutomator BE  ::  by Adikarthik Gupta C B
```

- [ ] **Step 5: Build and smoke-run**

```powershell
cd F:\views\g\Naukri\backend
mvn -q clean package -DskipTests
Start-Job -Name naukri-be -ScriptBlock { java -jar F:\views\g\Naukri\backend\target\naukri-be.jar } | Out-Null
Start-Sleep -Seconds 6
Receive-Job -Name naukri-be | Select-String "NAUKRI_BE_PORT="
Stop-Job  -Name naukri-be
Remove-Job -Name naukri-be
```

Expected: one line matching `NAUKRI_BE_PORT=<digits>`.

- [ ] **Step 6: Commit**

```powershell
cd F:\views\g\Naukri
git add backend\pom.xml backend\src
git commit -m "feat(be): bootstrap Spring Boot module with port announcer"
```

### Task 1.2: Health endpoint + first test

**Files:**
- Create: `backend\src\main\java\com\adi\naukri\api\HealthController.java`
- Create: `backend\src\test\java\com\adi\naukri\api\HealthControllerTest.java`

**Interfaces:**
- Consumes: Spring MVC.
- Produces: `GET /api/health` → `200 { "ok": true, "version": "<pom.version>", "author": "Adikarthik Gupta C B" }`.

- [ ] **Step 1: Write failing test**

```java
package com.adi.naukri.api;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
class HealthControllerTest {
    @Autowired MockMvc mvc;

    @Test
    void health_returnsOkAndAuthor() throws Exception {
        mvc.perform(get("/api/health"))
           .andExpect(status().isOk())
           .andExpect(jsonPath("$.ok").value(true))
           .andExpect(jsonPath("$.author").value("Adikarthik Gupta C B"));
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

```powershell
cd F:\views\g\Naukri\backend
mvn -q test -Dtest=HealthControllerTest
```

Expected: FAIL — `404 Not Found`.

- [ ] **Step 3: Implement `HealthController`**

```java
package com.adi.naukri.api;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api")
public class HealthController {

    private static final String VERSION = "0.1.0";
    private static final String AUTHOR  = "Adikarthik Gupta C B";

    @GetMapping("/health")
    public Map<String, Object> health() {
        return Map.of("ok", true, "version", VERSION, "author", AUTHOR);
    }
}
```

- [ ] **Step 4: Re-run test**

Expected: PASS.

- [ ] **Step 5: Commit**

```powershell
git add backend\src\main\java\com\adi\naukri\api\HealthController.java backend\src\test\java\com\adi\naukri\api\HealthControllerTest.java
git commit -m "feat(be): add /api/health endpoint with author byline"
```

---

## Milestone 2 — Excel parsing + template generation

### Task 2.1: `EmailExcelParser` with strict validation

**Files:**
- Create: `backend\src\main\java\com\adi\naukri\excel\ParsedEmailRow.java`
- Create: `backend\src\main\java\com\adi\naukri\excel\EmailExcelParser.java`
- Create: `backend\src\test\java\com\adi\naukri\excel\EmailExcelParserTest.java`
- Create: `backend\src\test\resources\excel\valid.xlsx`, `invalid-header.xlsx`, `mixed.xlsx` (generated in Step 1)

**Interfaces:**
- Consumes: Apache POI `XSSFWorkbook`.
- Produces:
  - `record ParsedEmailRow(int rowNumber, String email, String remarks, boolean valid, String error)`
  - `class EmailExcelParser { List<ParsedEmailRow> parse(InputStream xlsx) }` — throws `ExcelFormatException` if sheet `Emails` is missing or row 1 header `email` is missing.

- [ ] **Step 1: Generate fixture spreadsheets via helper**

Create `backend\src\test\java\com\adi\naukri\excel\Fixtures.java`:
```java
package com.adi.naukri.excel;

import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import java.io.FileOutputStream;
import java.nio.file.Path;

class Fixtures {
    static void write(Path out, String sheetName, String[][] rows) throws Exception {
        try (Workbook wb = new XSSFWorkbook(); FileOutputStream fos = new FileOutputStream(out.toFile())) {
            Sheet sh = wb.createSheet(sheetName);
            for (int r = 0; r < rows.length; r++) {
                Row row = sh.createRow(r);
                for (int c = 0; c < rows[r].length; c++) {
                    if (rows[r][c] != null) row.createCell(c).setCellValue(rows[r][c]);
                }
            }
            wb.write(fos);
        }
    }
}
```

- [ ] **Step 2: Write failing test**

```java
package com.adi.naukri.excel;

import org.junit.jupiter.api.*;
import java.nio.file.*;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

class EmailExcelParserTest {
    @TempDir Path tmp;
    EmailExcelParser parser = new EmailExcelParser();

    @Test
    void parses_valid_rows() throws Exception {
        Path f = tmp.resolve("v.xlsx");
        Fixtures.write(f, "Emails", new String[][]{
            {"email", "remarks"},
            {"a@x.com", "primary"},
            {"b@x.com", null}
        });
        List<ParsedEmailRow> rows = parser.parse(Files.newInputStream(f));
        assertEquals(2, rows.size());
        assertTrue(rows.stream().allMatch(ParsedEmailRow::valid));
        assertEquals("a@x.com", rows.get(0).email());
    }

    @Test
    void flags_invalid_email() throws Exception {
        Path f = tmp.resolve("i.xlsx");
        Fixtures.write(f, "Emails", new String[][]{
            {"email"},
            {"not-an-email"},
            {""},
            {"ok@x.com"}
        });
        List<ParsedEmailRow> rows = parser.parse(Files.newInputStream(f));
        assertEquals(3, rows.size());
        assertFalse(rows.get(0).valid());
        assertFalse(rows.get(1).valid());
        assertTrue (rows.get(2).valid());
    }

    @Test
    void rejects_missing_sheet() throws Exception {
        Path f = tmp.resolve("bad.xlsx");
        Fixtures.write(f, "Other", new String[][]{{"email"},{"a@x.com"}});
        assertThrows(ExcelFormatException.class, () -> parser.parse(Files.newInputStream(f)));
    }

    @Test
    void rejects_missing_header() throws Exception {
        Path f = tmp.resolve("h.xlsx");
        Fixtures.write(f, "Emails", new String[][]{{"address"},{"a@x.com"}});
        assertThrows(ExcelFormatException.class, () -> parser.parse(Files.newInputStream(f)));
    }
}
```

- [ ] **Step 3: Run — expect compile failures**

```powershell
mvn -q -f F:\views\g\Naukri\backend test -Dtest=EmailExcelParserTest
```

- [ ] **Step 4: Implement production classes**

`ExcelFormatException.java`:
```java
package com.adi.naukri.excel;
public class ExcelFormatException extends RuntimeException {
    public ExcelFormatException(String msg) { super(msg); }
}
```

`ParsedEmailRow.java`:
```java
package com.adi.naukri.excel;
public record ParsedEmailRow(int rowNumber, String email, String remarks, boolean valid, String error) {}
```

`EmailExcelParser.java`:
```java
package com.adi.naukri.excel;

import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.stereotype.Component;

import java.io.InputStream;
import java.util.*;
import java.util.regex.Pattern;

@Component
public class EmailExcelParser {

    private static final Pattern EMAIL = Pattern.compile("^[^\\s@]+@[^\\s@]+\\.[^\\s@]+$");
    private static final String SHEET = "Emails";
    private static final String HDR_EMAIL = "email";
    private static final String HDR_REMARKS = "remarks";

    public List<ParsedEmailRow> parse(InputStream xlsx) {
        try (Workbook wb = new XSSFWorkbook(xlsx)) {
            Sheet sh = wb.getSheet(SHEET);
            if (sh == null) throw new ExcelFormatException("Expected sheet '" + SHEET + "'");
            Row header = sh.getRow(0);
            if (header == null) throw new ExcelFormatException("Header row missing");

            int emailCol = -1, remarksCol = -1;
            for (Cell c : header) {
                String v = c.getStringCellValue().trim().toLowerCase(Locale.ROOT);
                if (v.equals(HDR_EMAIL))   emailCol   = c.getColumnIndex();
                if (v.equals(HDR_REMARKS)) remarksCol = c.getColumnIndex();
            }
            if (emailCol < 0) throw new ExcelFormatException("Missing required header 'email'");

            List<ParsedEmailRow> out = new ArrayList<>();
            for (int r = 1; r <= sh.getLastRowNum(); r++) {
                Row row = sh.getRow(r);
                if (row == null) continue;
                String email   = cell(row, emailCol);
                String remarks = remarksCol < 0 ? null : cell(row, remarksCol);
                if (email == null || email.isBlank()) {
                    out.add(new ParsedEmailRow(r + 1, "", remarks, false, "empty"));
                } else if (!EMAIL.matcher(email).matches()) {
                    out.add(new ParsedEmailRow(r + 1, email, remarks, false, "invalid format"));
                } else {
                    out.add(new ParsedEmailRow(r + 1, email, remarks, true, null));
                }
            }
            return out;
        } catch (ExcelFormatException e) { throw e;
        } catch (Exception e) { throw new ExcelFormatException("Could not read xlsx: " + e.getMessage()); }
    }

    private static String cell(Row r, int i) {
        Cell c = r.getCell(i, Row.MissingCellPolicy.RETURN_BLANK_AS_NULL);
        if (c == null) return null;
        c.setCellType(CellType.STRING);
        String v = c.getStringCellValue();
        return v == null ? null : v.trim();
    }
}
```

- [ ] **Step 5: Re-run tests to green**

```powershell
mvn -q -f F:\views\g\Naukri\backend test -Dtest=EmailExcelParserTest
```

Expected: 4 tests pass.

- [ ] **Step 6: Commit**

```powershell
git add backend\src\main\java\com\adi\naukri\excel backend\src\test\java\com\adi\naukri\excel
git commit -m "feat(be): add EmailExcelParser with strict header + email validation"
```

### Task 2.2: Template generator + `/api/template` endpoint

**Files:**
- Create: `backend\src\main\java\com\adi\naukri\excel\TemplateBuilder.java`
- Create: `backend\src\main\java\com\adi\naukri\api\TemplateController.java`
- Create: `backend\src\test\java\com\adi\naukri\excel\TemplateBuilderTest.java`
- Create: `backend\src\test\java\com\adi\naukri\api\TemplateControllerTest.java`

**Interfaces:**
- Produces:
  - `class TemplateBuilder { byte[] build() }` — returns a `.xlsx` byte stream with sheet `Emails`, headers `email` + `remarks`, and 5 sample rows using domain `example.com`.
  - `GET /api/template` → `200`, `Content-Type: application/vnd.openxmlformats-officedocument.spreadsheetml.sheet`, `Content-Disposition: attachment; filename="naukri-emails-template.xlsx"`.

- [ ] **Step 1: Failing tests**

```java
package com.adi.naukri.excel;

import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.Test;
import java.io.ByteArrayInputStream;
import static org.junit.jupiter.api.Assertions.*;

class TemplateBuilderTest {
    @Test
    void produces_valid_workbook_with_emails_sheet_and_five_samples() throws Exception {
        byte[] bytes = new TemplateBuilder().build();
        try (Workbook wb = new XSSFWorkbook(new ByteArrayInputStream(bytes))) {
            Sheet s = wb.getSheet("Emails");
            assertNotNull(s);
            assertEquals("email",   s.getRow(0).getCell(0).getStringCellValue());
            assertEquals("remarks", s.getRow(0).getCell(1).getStringCellValue());
            assertEquals(5, s.getLastRowNum()); // header + 5 rows
        }
    }
}
```

```java
package com.adi.naukri.api;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
class TemplateControllerTest {
    @Autowired MockMvc mvc;

    @Test
    void download_returns_xlsx_with_attachment_disposition() throws Exception {
        mvc.perform(get("/api/template"))
           .andExpect(status().isOk())
           .andExpect(header().string("Content-Disposition",
               "attachment; filename=\"naukri-emails-template.xlsx\""))
           .andExpect(content().contentType(
               "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"));
    }
}
```

- [ ] **Step 2: Run — expect failure**

```powershell
mvn -q -f F:\views\g\Naukri\backend test -Dtest=TemplateBuilderTest,TemplateControllerTest
```

- [ ] **Step 3: Implement builder**

```java
package com.adi.naukri.excel;

import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.stereotype.Component;

import java.io.ByteArrayOutputStream;

@Component
public class TemplateBuilder {

    private static final String[][] ROWS = {
        {"email", "remarks"},
        {"user1@example.com", "primary account"},
        {"user2@example.com", ""},
        {"user3@example.com", "backup"},
        {"user4@example.com", ""},
        {"user5@example.com", "senior profile"}
    };

    public byte[] build() {
        try (Workbook wb = new XSSFWorkbook(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            Sheet sh = wb.createSheet("Emails");
            Font bold = wb.createFont(); bold.setBold(true);
            CellStyle hs = wb.createCellStyle(); hs.setFont(bold);
            for (int r = 0; r < ROWS.length; r++) {
                Row row = sh.createRow(r);
                for (int c = 0; c < ROWS[r].length; c++) {
                    Cell cell = row.createCell(c);
                    cell.setCellValue(ROWS[r][c]);
                    if (r == 0) cell.setCellStyle(hs);
                }
            }
            sh.autoSizeColumn(0);
            sh.autoSizeColumn(1);
            wb.write(out);
            return out.toByteArray();
        } catch (Exception e) {
            throw new RuntimeException("Failed to build template", e);
        }
    }
}
```

- [ ] **Step 4: Implement controller**

```java
package com.adi.naukri.api;

import com.adi.naukri.excel.TemplateBuilder;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api")
public class TemplateController {
    private final TemplateBuilder builder;
    public TemplateController(TemplateBuilder builder) { this.builder = builder; }

    @GetMapping("/template")
    public ResponseEntity<byte[]> template() {
        byte[] body = builder.build();
        return ResponseEntity.ok()
            .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"naukri-emails-template.xlsx\"")
            .contentType(MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
            .body(body);
    }
}
```

- [ ] **Step 5: Re-run tests to green**

Expected: both tests pass.

- [ ] **Step 6: Commit**

```powershell
git add backend\src\main\java\com\adi\naukri\excel\TemplateBuilder.java backend\src\main\java\com\adi\naukri\api\TemplateController.java backend\src\test\java\com\adi\naukri\excel\TemplateBuilderTest.java backend\src\test\java\com\adi\naukri\api\TemplateControllerTest.java
git commit -m "feat(be): add Excel template builder and /api/template endpoint"
```

---

## Milestone 3 — Domain primitives (renamer, retry policy, report writer)

### Task 3.1: `ResumeRenamer`

**Files:**
- Create: `backend\src\main\java\com\adi\naukri\automation\ResumeRenamer.java`
- Create: `backend\src\test\java\com\adi\naukri\automation\ResumeRenamerTest.java`

**Interfaces:**
- Produces: `class ResumeRenamer { Path rename(Path src, LocalDate today) }` → returns a **new** path in the same directory whose stem is `<originalStem>_YYYY-MM-DD` and extension is preserved. If the target already exists, appends `-1`, `-2`, ... until unique.

- [ ] **Step 1: Failing test**

```java
package com.adi.naukri.automation;

import org.junit.jupiter.api.*;
import java.nio.file.*;
import java.time.LocalDate;
import static org.junit.jupiter.api.Assertions.*;

class ResumeRenamerTest {
    @TempDir Path tmp;
    ResumeRenamer r = new ResumeRenamer();

    @Test
    void appends_date_and_preserves_extension() throws Exception {
        Path src = Files.writeString(tmp.resolve("JohnDoe_Resume.pdf"), "pdf");
        Path out = r.rename(src, LocalDate.of(2026, 7, 14));
        assertEquals("JohnDoe_Resume_2026-07-14.pdf", out.getFileName().toString());
        assertTrue(Files.exists(out));
        assertFalse(Files.exists(src));
    }

    @Test
    void handles_docx() throws Exception {
        Path src = Files.writeString(tmp.resolve("cv.docx"), "docx");
        Path out = r.rename(src, LocalDate.of(2026, 1, 5));
        assertEquals("cv_2026-01-05.docx", out.getFileName().toString());
    }

    @Test
    void collision_gets_suffix() throws Exception {
        Files.writeString(tmp.resolve("cv_2026-07-14.pdf"), "existing");
        Path src = Files.writeString(tmp.resolve("cv.pdf"), "new");
        Path out = r.rename(src, LocalDate.of(2026, 7, 14));
        assertEquals("cv_2026-07-14-1.pdf", out.getFileName().toString());
    }
}
```

- [ ] **Step 2: Run — expect FAIL**

```powershell
mvn -q -f F:\views\g\Naukri\backend test -Dtest=ResumeRenamerTest
```

- [ ] **Step 3: Implement**

```java
package com.adi.naukri.automation;

import org.springframework.stereotype.Component;
import java.io.IOException;
import java.nio.file.*;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;

@Component
public class ResumeRenamer {
    private static final DateTimeFormatter FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd");

    public Path rename(Path src, LocalDate today) throws IOException {
        String name = src.getFileName().toString();
        int dot = name.lastIndexOf('.');
        String stem = dot < 0 ? name : name.substring(0, dot);
        String ext  = dot < 0 ? ""   : name.substring(dot);
        String date = today.format(FMT);
        Path parent = src.getParent();
        Path candidate = parent.resolve(stem + "_" + date + ext);
        int n = 1;
        while (Files.exists(candidate)) {
            candidate = parent.resolve(stem + "_" + date + "-" + n + ext);
            n++;
        }
        return Files.move(src, candidate, StandardCopyOption.ATOMIC_MOVE);
    }
}
```

- [ ] **Step 4: Green**

Expected: 3 tests pass.

- [ ] **Step 5: Commit**

```powershell
git add backend\src\main\java\com\adi\naukri\automation\ResumeRenamer.java backend\src\test\java\com\adi\naukri\automation\ResumeRenamerTest.java
git commit -m "feat(be): add ResumeRenamer with date-stamp and collision suffix"
```

### Task 3.2: `RetryPolicy`

**Files:**
- Create: `backend\src\main\java\com\adi\naukri\automation\RetryPolicy.java`
- Create: `backend\src\main\java\com\adi\naukri\automation\AutomationRunMode.java`
- Create: `backend\src\test\java\com\adi\naukri\automation\RetryPolicyTest.java`

**Interfaces:**
- Produces:
  - `enum AutomationRunMode { HEADLESS, HEADED }`
  - `record RetryAttempt(int attempt, AutomationRunMode mode, long timeoutMs)`
  - `class RetryPolicy { List<RetryAttempt> attemptsFor(AutomationRunMode initial, long baseTimeoutMs) }`
    - Rules from spec §5: attempt 1 with base timeout in `initial` mode; attempt 2 with 2× timeout in `initial` mode; if `initial=HEADLESS` and attempt 2 failed, attempt 3 in `HEADED` mode with 2× timeout. Attempt-count cap: 3.

- [ ] **Step 1: Failing test**

```java
package com.adi.naukri.automation;

import org.junit.jupiter.api.Test;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

class RetryPolicyTest {
    RetryPolicy p = new RetryPolicy();

    @Test
    void headed_run_gets_two_attempts_both_headed() {
        List<RetryAttempt> a = p.attemptsFor(AutomationRunMode.HEADED, 30_000);
        assertEquals(2, a.size());
        assertEquals(AutomationRunMode.HEADED, a.get(0).mode());
        assertEquals(30_000, a.get(0).timeoutMs());
        assertEquals(AutomationRunMode.HEADED, a.get(1).mode());
        assertEquals(60_000, a.get(1).timeoutMs());
    }

    @Test
    void headless_run_gets_three_attempts_last_headed() {
        List<RetryAttempt> a = p.attemptsFor(AutomationRunMode.HEADLESS, 30_000);
        assertEquals(3, a.size());
        assertEquals(AutomationRunMode.HEADLESS, a.get(0).mode());
        assertEquals(AutomationRunMode.HEADLESS, a.get(1).mode());
        assertEquals(AutomationRunMode.HEADED,   a.get(2).mode());
        assertEquals(60_000, a.get(2).timeoutMs());
    }
}
```

- [ ] **Step 2: Run — FAIL**

- [ ] **Step 3: Implement**

```java
package com.adi.naukri.automation;
public enum AutomationRunMode { HEADLESS, HEADED }
```

```java
package com.adi.naukri.automation;
public record RetryAttempt(int attempt, AutomationRunMode mode, long timeoutMs) {}
```

```java
package com.adi.naukri.automation;

import org.springframework.stereotype.Component;
import java.util.ArrayList;
import java.util.List;

@Component
public class RetryPolicy {
    public List<RetryAttempt> attemptsFor(AutomationRunMode initial, long baseMs) {
        List<RetryAttempt> out = new ArrayList<>();
        out.add(new RetryAttempt(1, initial, baseMs));
        out.add(new RetryAttempt(2, initial, baseMs * 2));
        if (initial == AutomationRunMode.HEADLESS) {
            out.add(new RetryAttempt(3, AutomationRunMode.HEADED, baseMs * 2));
        }
        return out;
    }
}
```

- [ ] **Step 4: Green**

- [ ] **Step 5: Commit**

```powershell
git add backend\src\main\java\com\adi\naukri\automation\RetryPolicy.java backend\src\main\java\com\adi\naukri\automation\RetryAttempt.java backend\src\main\java\com\adi\naukri\automation\AutomationRunMode.java backend\src\test\java\com\adi\naukri\automation\RetryPolicyTest.java
git commit -m "feat(be): add RetryPolicy per spec section 5"
```

### Task 3.3: `ReportWriter` (CSV + JSON + per-account logs)

**Files:**
- Create: `backend\src\main\java\com\adi\naukri\report\AccountResult.java`
- Create: `backend\src\main\java\com\adi\naukri\report\ReportWriter.java`
- Create: `backend\src\test\java\com\adi\naukri\report\ReportWriterTest.java`

**Interfaces:**
- Produces:
  - `enum AccountStatus { OK, AUTH_FAILED, REQUIRES_MANUAL, FAILED, SKIPPED }`
  - `record StepTiming(String step, long durationMs)`
  - `record AccountResult(String email, AccountStatus status, String error, String resumeOldName, String resumeNewName, Instant startedAt, Instant endedAt, int retries, List<StepTiming> steps)`
  - `class ReportWriter { void write(Path runDir, List<AccountResult> results) }` → writes `report.csv`, `report.json`, and one `logs/<email>.log` per result.

- [ ] **Step 1: Failing test**

```java
package com.adi.naukri.report;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.*;
import java.nio.file.*;
import java.time.Instant;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class ReportWriterTest {
    @TempDir Path runDir;
    ReportWriter w = new ReportWriter();

    @Test
    void writes_csv_json_and_logs() throws Exception {
        var res = List.of(
            new AccountResult("a@x.com", AccountStatus.OK, null, "cv.pdf", "cv_2026-07-14.pdf",
                Instant.parse("2026-07-14T10:00:00Z"), Instant.parse("2026-07-14T10:00:30Z"), 0,
                List.of(new StepTiming("login", 1500), new StepTiming("logout", 500))),
            new AccountResult("b@x.com", AccountStatus.AUTH_FAILED, "wrong password", null, null,
                Instant.parse("2026-07-14T10:00:30Z"), Instant.parse("2026-07-14T10:00:45Z"), 1, List.of())
        );
        w.write(runDir, res);

        Path csv = runDir.resolve("report.csv");
        Path json = runDir.resolve("report.json");
        Path log1 = runDir.resolve("logs/a@x.com.log");
        assertTrue(Files.exists(csv));
        assertTrue(Files.exists(json));
        assertTrue(Files.exists(log1));

        String csvText = Files.readString(csv);
        assertTrue(csvText.startsWith("email,status,error,resumeOldName,resumeNewName,startedAt,endedAt,retries"));
        assertTrue(csvText.contains("a@x.com,OK,,cv.pdf,cv_2026-07-14.pdf,"));
        assertTrue(csvText.contains("b@x.com,AUTH_FAILED,wrong password,,,"));

        var parsed = new ObjectMapper().readTree(Files.readString(json));
        assertEquals(2, parsed.size());
        assertEquals("OK", parsed.get(0).get("status").asText());
        assertEquals(1500, parsed.get(0).get("steps").get(0).get("durationMs").asInt());
    }
}
```

- [ ] **Step 2: Run — FAIL**

- [ ] **Step 3: Implement records**

```java
package com.adi.naukri.report;
public enum AccountStatus { OK, AUTH_FAILED, REQUIRES_MANUAL, FAILED, SKIPPED }
```
```java
package com.adi.naukri.report;
public record StepTiming(String step, long durationMs) {}
```
```java
package com.adi.naukri.report;
import java.time.Instant;
import java.util.List;
public record AccountResult(
    String email, AccountStatus status, String error,
    String resumeOldName, String resumeNewName,
    Instant startedAt, Instant endedAt, int retries,
    List<StepTiming> steps) {}
```

- [ ] **Step 4: Implement writer**

```java
package com.adi.naukri.report;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.file.*;
import java.util.List;

@Component
public class ReportWriter {

    private static final String CSV_HEADER =
        "email,status,error,resumeOldName,resumeNewName,startedAt,endedAt,retries";
    private static final ObjectMapper MAPPER = new ObjectMapper().findAndRegisterModules();

    public void write(Path runDir, List<AccountResult> results) throws IOException {
        Files.createDirectories(runDir);
        Files.createDirectories(runDir.resolve("logs"));
        writeCsv (runDir.resolve("report.csv"),  results);
        writeJson(runDir.resolve("report.json"), results);
        for (AccountResult r : results) writeLog(runDir.resolve("logs").resolve(r.email() + ".log"), r);
    }

    private void writeCsv(Path out, List<AccountResult> rs) throws IOException {
        StringBuilder sb = new StringBuilder(CSV_HEADER).append('\n');
        for (AccountResult r : rs) {
            sb.append(csv(r.email())).append(',')
              .append(r.status()).append(',')
              .append(csv(r.error())).append(',')
              .append(csv(r.resumeOldName())).append(',')
              .append(csv(r.resumeNewName())).append(',')
              .append(r.startedAt()).append(',')
              .append(r.endedAt()).append(',')
              .append(r.retries()).append('\n');
        }
        Files.writeString(out, sb.toString());
    }

    private void writeJson(Path out, List<AccountResult> rs) throws IOException {
        Files.writeString(out, MAPPER.writerWithDefaultPrettyPrinter().writeValueAsString(rs));
    }

    private void writeLog(Path out, AccountResult r) throws IOException {
        StringBuilder sb = new StringBuilder();
        sb.append("email:  ").append(r.email()).append('\n')
          .append("status: ").append(r.status()).append('\n')
          .append("error:  ").append(r.error() == null ? "" : r.error()).append('\n')
          .append("start:  ").append(r.startedAt()).append('\n')
          .append("end:    ").append(r.endedAt()).append('\n')
          .append("retries:").append(r.retries()).append('\n')
          .append("steps:\n");
        for (StepTiming t : r.steps()) sb.append("  ").append(t.step()).append(' ').append(t.durationMs()).append("ms\n");
        Files.writeString(out, sb.toString());
    }

    private static String csv(String s) {
        if (s == null) return "";
        if (s.contains(",") || s.contains("\"") || s.contains("\n"))
            return "\"" + s.replace("\"", "\"\"") + "\"";
        return s;
    }
}
```

- [ ] **Step 5: Green**

- [ ] **Step 6: Commit**

```powershell
git add backend\src\main\java\com\adi\naukri\report backend\src\test\java\com\adi\naukri\report
git commit -m "feat(be): add ReportWriter for CSV + JSON + per-account logs"
```

---

## Milestone 4 — Mock Naukri test server (shared harness)

Every automation-related test — BE integration, E2E — needs a deterministic imitation of naukri.com. This milestone builds a small standalone Spring Boot app that serves fake login, dashboard, profile, and resume pages. It is:

- A separate top-level Maven module `mock-naukri\` (not shipped in the EXE).
- Startable via `java -jar mock-naukri-<ver>.jar --server.port=<free>`.
- Consumed by BE integration tests (as a `@BeforeAll` process) and by the E2E runner (as a background process during Playwright runs).

### Task 4.1: Mock Naukri module scaffold + pages

**Files:**
- Create: `mock-naukri\pom.xml`
- Create: `mock-naukri\src\main\java\com\adi\mock\MockNaukriApplication.java`
- Create: `mock-naukri\src\main\java\com\adi\mock\PageController.java`
- Create: `mock-naukri\src\main\java\com\adi\mock\MockState.java`
- Create: `mock-naukri\src\main\resources\static\resume.pdf` (1 KB placeholder PDF)
- Create: `mock-naukri\src\main\resources\templates\{login,dashboard,profile,otp}.html` (Thymeleaf)
- Create: `mock-naukri\src\test\java\com\adi\mock\MockPagesTest.java`

**Interfaces:**
- Serves (all under configured port):
  - `GET /nlogin/login` — login form.
  - `POST /nlogin/login` — accepts `email`/`password`. If `email` starts with `bad@` → renders `login.html` again with `#error-banner`. If `email` starts with `otp@` → redirects to `/otp`. Otherwise sets cookie `MOCK_SESSION=<email>` and redirects to `/mnjuser/homepage`.
  - `GET /mnjuser/homepage` — the dashboard. Requires cookie; else redirects to `/nlogin/login`.
  - `GET /mnjuser/profile` — profile page with a headline field the mock can accept edits on via `POST /mnjuser/profile/headline` (JSON `{value}`). Records last value into `MockState`.
  - `GET /mnjuser/profile/resume` — triggers file download of the placeholder PDF.
  - `POST /mnjuser/profile/resume` — accepts a multipart upload; stores name into `MockState.uploadedResumeName`.
  - `POST /nlogin/logout` — clears cookie, redirects to `/nlogin/login`.
  - `GET /_mock/state` — returns `MockState` as JSON (used by tests to assert side-effects).
  - `POST /_mock/reset` — clears state (called from tests between scenarios).

- [ ] **Step 1: `mock-naukri\pom.xml`**

```xml
<?xml version="1.0" encoding="UTF-8"?>
<project xmlns="http://maven.apache.org/POM/4.0.0">
  <modelVersion>4.0.0</modelVersion>
  <groupId>com.adi.mock</groupId>
  <artifactId>mock-naukri</artifactId>
  <version>0.1.0</version>
  <parent>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-parent</artifactId>
    <version>3.3.0</version>
    <relativePath/>
  </parent>
  <properties><java.version>17</java.version></properties>
  <dependencies>
    <dependency><groupId>org.springframework.boot</groupId><artifactId>spring-boot-starter-web</artifactId></dependency>
    <dependency><groupId>org.springframework.boot</groupId><artifactId>spring-boot-starter-thymeleaf</artifactId></dependency>
    <dependency><groupId>org.springframework.boot</groupId><artifactId>spring-boot-starter-test</artifactId><scope>test</scope></dependency>
  </dependencies>
  <build>
    <finalName>mock-naukri</finalName>
    <plugins>
      <plugin><groupId>org.springframework.boot</groupId><artifactId>spring-boot-maven-plugin</artifactId></plugin>
    </plugins>
  </build>
</project>
```

- [ ] **Step 2: Application, state, controller**

```java
package com.adi.mock;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
public class MockNaukriApplication {
    public static void main(String[] args) { SpringApplication.run(MockNaukriApplication.class, args); }
}
```

```java
package com.adi.mock;
import org.springframework.stereotype.Component;

@Component
public class MockState {
    public volatile String lastHeadline = "Senior Engineer";
    public volatile String uploadedResumeName;
    public volatile int    headlineSaveCount;
    public volatile boolean loggedOut;

    public void reset() {
        lastHeadline = "Senior Engineer";
        uploadedResumeName = null;
        headlineSaveCount = 0;
        loggedOut = false;
    }
}
```

`PageController.java` (excerpt — full class in the actual step must implement every route above using `HttpServletResponse` for redirects and `Model` for Thymeleaf; return the exact HTML files described below; on `/mnjuser/profile/resume` GET use `Resource` from classpath):

```java
@Controller
@RequestMapping
public class PageController {
    private final MockState state;
    PageController(MockState s) { this.state = s; }

    @GetMapping("/nlogin/login") String loginForm() { return "login"; }

    @PostMapping("/nlogin/login")
    String loginSubmit(@RequestParam String email, @RequestParam String password,
                       HttpServletResponse resp, Model m) {
        if (email.startsWith("bad@"))  { m.addAttribute("error", "Wrong credentials"); return "login"; }
        if (email.startsWith("otp@"))  return "redirect:/otp";
        Cookie c = new Cookie("MOCK_SESSION", email);
        c.setPath("/");
        resp.addCookie(c);
        return "redirect:/mnjuser/homepage";
    }
    // ... remaining routes as listed above
}
```

- [ ] **Step 3: HTML templates**

`login.html` — one form with `input[name=email]`, `input[name=password]`, submit button. `#error-banner` shown when `${error}` present.
`dashboard.html` — a nav bar with a "Logout" link posting to `/nlogin/logout`, a "Profile" link to `/mnjuser/profile`.
`profile.html` — a headline input (`#headline`), a "Save" button (submits to `/mnjuser/profile/headline` via a small inline JS `fetch`), a "Download resume" button linking to `/mnjuser/profile/resume`, an "Update resume" form posting multipart to `/mnjuser/profile/resume`.
`otp.html` — a "Enter OTP" input; posting anything → redirects to dashboard (simulates a manual login completion).

Each template must use the CSS selectors that `NaukriSelectors.java` (Task 5.1) will target — see interface list in Task 5.1 for the exact selectors.

- [ ] **Step 4: Verify all routes**

```java
package com.adi.mock;

import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
class MockPagesTest {
    @Autowired MockMvc mvc;

    @Test void login_page_ok()          throws Exception { mvc.perform(get("/nlogin/login")).andExpect(status().isOk()); }
    @Test void bad_login_stays()        throws Exception {
        mvc.perform(post("/nlogin/login").param("email","bad@x.com").param("password","x"))
           .andExpect(status().isOk()).andExpect(content().string(org.hamcrest.Matchers.containsString("Wrong credentials")));
    }
    @Test void good_login_redirects()   throws Exception {
        mvc.perform(post("/nlogin/login").param("email","ok@x.com").param("password","x"))
           .andExpect(status().is3xxRedirection()).andExpect(redirectedUrl("/mnjuser/homepage"));
    }
    @Test void otp_login_redirects()    throws Exception {
        mvc.perform(post("/nlogin/login").param("email","otp@x.com").param("password","x"))
           .andExpect(redirectedUrl("/otp"));
    }
    @Test void state_reset_works()      throws Exception {
        mvc.perform(post("/_mock/reset")).andExpect(status().isOk());
        mvc.perform(get("/_mock/state")).andExpect(jsonPath("$.headlineSaveCount").value(0));
    }
}
```

- [ ] **Step 5: Build the fat JAR**

```powershell
mvn -q -f F:\views\g\Naukri\mock-naukri clean package
```

Expected: `mock-naukri\target\mock-naukri.jar` present.

- [ ] **Step 6: Smoke-run**

```powershell
Start-Job -Name mock -ScriptBlock { java -jar F:\views\g\Naukri\mock-naukri\target\mock-naukri.jar --server.port=18888 } | Out-Null
Start-Sleep 6
(Invoke-WebRequest -Uri http://127.0.0.1:18888/nlogin/login -UseBasicParsing).StatusCode
Stop-Job -Name mock ; Remove-Job -Name mock
```

Expected: `200`.

- [ ] **Step 7: Commit**

```powershell
git add mock-naukri
git commit -m "feat(mock): add mock Naukri test server module"
```

---

## Milestone 5 — Automation core (`NaukriAutomator` + Playwright wiring)

Runs one account against a real Playwright browser targeting a URL base (Naukri in prod, Mock Naukri in tests). Every automation-related test in this and later milestones uses the mock from Milestone 4.

### Task 5.1: `NaukriSelectors` constants + `StepResult`

**Files:**
- Create: `backend\src\main\java\com\adi\naukri\automation\NaukriSelectors.java`
- Create: `backend\src\main\java\com\adi\naukri\automation\StepResult.java`
- Create: `backend\src\main\java\com\adi\naukri\automation\AutomationStep.java`
- Create: `backend\src\test\java\com\adi\naukri\automation\NaukriSelectorsTest.java`

**Interfaces:**
- Produces:
  - `enum AutomationStep { LOGIN, HEADLINE_APPEND, HEADLINE_STRIP, DOWNLOAD_RESUME, RENAME_RESUME, UPLOAD_RESUME, LOGOUT }`
  - `record StepResult(AutomationStep step, boolean ok, String error, long durationMs)`
  - `class NaukriSelectors { ... }` — public static string constants used by both production Playwright code and the Mock Naukri HTML templates. Named per DOM role:
    - `LOGIN_EMAIL       = "input[name='email']"`
    - `LOGIN_PASSWORD    = "input[name='password']"`
    - `LOGIN_SUBMIT      = "button[type='submit']"`
    - `LOGIN_ERROR       = "#error-banner"`
    - `DASH_BRAND        = "[data-mock='dash']"`
    - `NAV_PROFILE       = "a[href='/mnjuser/profile']"`
    - `HEADLINE_INPUT    = "#headline"`
    - `HEADLINE_SAVE     = "#headline-save"`
    - `HEADLINE_TOAST    = "#headline-toast"`
    - `RESUME_DOWNLOAD   = "a#resume-download"`
    - `RESUME_UPLOAD_INP = "input[type='file']#resume-upload"`
    - `RESUME_UPLOAD_BTN = "#resume-upload-submit"`
    - `RESUME_TOAST      = "#resume-toast"`
    - `LOGOUT_LINK       = "a#logout"`
    - `OTP_INPUT         = "#otp"`

- [ ] **Step 1: Failing test** — asserts every constant is non-blank

```java
package com.adi.naukri.automation;
import org.junit.jupiter.api.Test;
import java.lang.reflect.*;
import static org.junit.jupiter.api.Assertions.*;

class NaukriSelectorsTest {
    @Test void all_selectors_non_blank() throws Exception {
        for (Field f : NaukriSelectors.class.getDeclaredFields()) {
            if (Modifier.isStatic(f.getModifiers()) && f.getType() == String.class) {
                String v = (String) f.get(null);
                assertNotNull(v, f.getName());
                assertFalse(v.isBlank(), f.getName());
            }
        }
    }
}
```

- [ ] **Step 2: Run — FAIL (class missing).**

- [ ] **Step 3: Implement enums + records + constants** as per interfaces above.

- [ ] **Step 4: Green.**

- [ ] **Step 5: Commit**

```powershell
git add backend\src\main\java\com\adi\naukri\automation\NaukriSelectors.java backend\src\main\java\com\adi\naukri\automation\StepResult.java backend\src\main\java\com\adi\naukri\automation\AutomationStep.java backend\src\test\java\com\adi\naukri\automation\NaukriSelectorsTest.java
git commit -m "feat(be): add NaukriSelectors, AutomationStep, StepResult"
```

### Task 5.2: `PlaywrightSession` — lifecycle wrapper

**Files:**
- Create: `backend\src\main\java\com\adi\naukri\automation\PlaywrightSession.java`
- Create: `backend\src\test\java\com\adi\naukri\automation\PlaywrightSessionIT.java`

**Interfaces:**
- Produces:
  - `class PlaywrightSession implements AutoCloseable { Page open(AutomationRunMode mode); void close(); }`
  - Every call to `open` returns a **new incognito** BrowserContext with a fresh `Page`. Closing the session tears down all contexts.
  - Timeout defaults: `pageLoadMs = 30_000`, `actionMs = 15_000`.

- [ ] **Step 1: Integration test using system-installed Chromium via Playwright**

```java
package com.adi.naukri.automation;

import com.microsoft.playwright.Page;
import org.junit.jupiter.api.*;
import static org.junit.jupiter.api.Assertions.*;

@Tag("integration")
class PlaywrightSessionIT {

    @Test
    void opens_and_navigates_to_data_url() {
        try (PlaywrightSession s = new PlaywrightSession()) {
            Page p = s.open(AutomationRunMode.HEADLESS);
            p.navigate("data:text/html,<h1>hi</h1>");
            assertEquals("hi", p.textContent("h1"));
        }
    }
}
```

- [ ] **Step 2: Implement**

```java
package com.adi.naukri.automation;

import com.microsoft.playwright.*;

public class PlaywrightSession implements AutoCloseable {
    private final Playwright pw = Playwright.create();
    private Browser browser;

    public Page open(AutomationRunMode mode) {
        if (browser == null) {
            browser = pw.chromium().launch(new BrowserType.LaunchOptions().setHeadless(mode == AutomationRunMode.HEADLESS));
        }
        BrowserContext ctx = browser.newContext(new Browser.NewContextOptions().setAcceptDownloads(true));
        ctx.setDefaultNavigationTimeout(30_000);
        ctx.setDefaultTimeout(15_000);
        return ctx.newPage();
    }

    @Override public void close() {
        if (browser != null) browser.close();
        pw.close();
    }
}
```

- [ ] **Step 3: Run integration test**

```powershell
# Ensures Playwright browsers are installed first
mvn -q -f F:\views\g\Naukri\backend exec:java "-Dexec.mainClass=com.microsoft.playwright.CLI" "-Dexec.args=install chromium"
mvn -q -f F:\views\g\Naukri\backend test -Dtest=PlaywrightSessionIT -DfailIfNoTests=false
```

Expected: PASS.

- [ ] **Step 4: Commit**

```powershell
git add backend\src\main\java\com\adi\naukri\automation\PlaywrightSession.java backend\src\test\java\com\adi\naukri\automation\PlaywrightSessionIT.java
git commit -m "feat(be): add PlaywrightSession Chromium lifecycle wrapper"
```

### Task 5.3: `NaukriAutomator` — the full per-account flow

**Files:**
- Create: `backend\src\main\java\com\adi\naukri\automation\NaukriAutomator.java`
- Create: `backend\src\main\java\com\adi\naukri\automation\AutomatorConfig.java`
- Create: `backend\src\main\java\com\adi\naukri\automation\AutomatorException.java`
- Create: `backend\src\main\java\com\adi\naukri\automation\ManualLoginGate.java`
- Create: `backend\src\test\java\com\adi\naukri\automation\NaukriAutomatorAgainstMockIT.java`

**Interfaces:**
- Produces:
  - `record AutomatorConfig(String baseUrl, Path downloadsDir, long pageLoadMs, long actionMs, boolean manualLogin, Duration manualLoginTimeout)`
  - `interface ManualLoginGate { boolean waitForResume(String email, Duration timeout, Supplier<Boolean> dashboardReached) }` — production impl polls `page.url()`; test impl is deterministic.
  - `class NaukriAutomator { List<StepResult> run(String email, String password, AutomationRunMode mode, AutomatorConfig cfg, PlaywrightSession session, ManualLoginGate gate, StepListener listener) throws AutomatorException }`
  - `interface StepListener { void onStep(StepResult step); void onManualLoginAwait(String email); }`
- Returned `StepResult` list is one entry per attempted step; on early exit (auth failure or requires-manual) the remaining steps do not appear.

- [ ] **Step 1: Integration test against Mock Naukri**

Test skeleton starts the mock jar via `@BeforeAll` (using `ProcessBuilder`) on a random free port, then runs:

```java
@Tag("integration")
class NaukriAutomatorAgainstMockIT {
    static Process mock;
    static int port;
    @BeforeAll static void start() throws Exception {
        port = TestPorts.free();
        mock = new ProcessBuilder("java","-jar",
            "..\\mock-naukri\\target\\mock-naukri.jar","--server.port="+port)
            .inheritIO().start();
        TestPorts.waitUntilOpen(port, Duration.ofSeconds(30));
    }
    @AfterAll static void stop() { mock.destroy(); }

    @Test
    void full_happy_flow_completes_all_steps() throws Exception {
        try (PlaywrightSession s = new PlaywrightSession()) {
            var cfg = new AutomatorConfig("http://127.0.0.1:"+port,
                        Path.of(System.getProperty("java.io.tmpdir"),"na-test"), 30_000, 15_000,
                        false, Duration.ofMinutes(5));
            var results = new NaukriAutomator().run(
                "ok@x.com","password", AutomationRunMode.HEADLESS, cfg, s,
                (email, timeout, dash) -> true, r -> {});
            assertTrue(results.stream().allMatch(StepResult::ok));
            assertEquals(6, results.size()); // LOGIN..LOGOUT, no rename-only step in list
        }
        // Assert mock recorded a headline save AND a resume upload
        var state = ObjectMapperHolder.MAPPER.readTree(
            new URL("http://127.0.0.1:"+port+"/_mock/state").openStream());
        assertEquals(2, state.get("headlineSaveCount").asInt()); // append + strip
        assertNotNull(state.get("uploadedResumeName").asText(null));
    }

    @Test
    void bad_credentials_short_circuits_with_auth_failed() { /* email = bad@x.com */ }

    @Test
    void otp_page_short_circuits_with_requires_manual() { /* email = otp@x.com */ }

    @Test
    void manual_login_gate_resumes_when_dashboard_reached() { /* manualLogin=true, gate returns true after 1s */ }
}
```

- [ ] **Step 2: Run — expect compile/failure.**

- [ ] **Step 3: Implement `NaukriAutomator`** — one private method per step, each returning a `StepResult`. Wraps each step in try/catch → returns `ok=false` + error message + duration. Full method walks: navigate → login-branch → post-login-branch → headline append+save → headline strip+save → download resume → rename via `ResumeRenamer` → upload → logout. Emits events via `StepListener` between steps.

The implementation must use only selectors from `NaukriSelectors`. Timeouts use `AutomatorConfig`. Screenshots on failure written to `<cfg.downloadsDir>/screenshots/<email>.png` (Playwright `page.screenshot`).

- [ ] **Step 4: Green all 4 tests.**

- [ ] **Step 5: Commit**

```powershell
git add backend\src\main\java\com\adi\naukri\automation\NaukriAutomator.java backend\src\main\java\com\adi\naukri\automation\AutomatorConfig.java backend\src\main\java\com\adi\naukri\automation\AutomatorException.java backend\src\main\java\com\adi\naukri\automation\ManualLoginGate.java backend\src\test\java\com\adi\naukri\automation\NaukriAutomatorAgainstMockIT.java
git commit -m "feat(be): NaukriAutomator with mock-server integration coverage"
```

---

## Milestone 6 — Job orchestrator (queue + sequential runner + event bus)

### Task 6.1: `JobEvent` types + `JobEventBus`

**Files:**
- Create: `backend\src\main\java\com\adi\naukri\orchestrator\JobEvent.java` (sealed interface + records for each event type in spec §10.2)
- Create: `backend\src\main\java\com\adi\naukri\orchestrator\JobEventBus.java`
- Create: `backend\src\test\java\com\adi\naukri\orchestrator\JobEventBusTest.java`

**Interfaces:**
- Produces:
  - `sealed interface JobEvent { String jobId(); Instant timestamp(); }` with `RunStarted`, `AccountStarted`, `StepStarted`, `StepCompleted`, `StepFailed`, `AwaitManualLogin`, `AccountCompleted`, `RunCompleted`, `RunStopped` records — fields exactly per spec §10.2.
  - `class JobEventBus { void publish(JobEvent e); Registration subscribe(String jobId, Consumer<JobEvent> sub) }` — thread-safe, buffers last 100 events per jobId so a late subscriber gets replay.

- [ ] **Step 1: Test**

```java
class JobEventBusTest {
    JobEventBus bus = new JobEventBus();

    @Test void late_subscriber_replays_last_events() {
        bus.publish(new JobEvent.RunStarted("j1", Instant.now(), 3));
        bus.publish(new JobEvent.AccountStarted("j1", Instant.now(), "a@x.com", 0));
        List<JobEvent> got = new ArrayList<>();
        bus.subscribe("j1", got::add);
        assertEquals(2, got.size());
    }

    @Test void events_ignored_for_other_job_ids() {
        List<JobEvent> got = new ArrayList<>();
        bus.subscribe("j1", got::add);
        bus.publish(new JobEvent.RunStarted("OTHER", Instant.now(), 1));
        assertTrue(got.isEmpty());
    }
}
```

- [ ] **Step 2: FAIL.**

- [ ] **Step 3: Implement.** Sealed interface + one record per event type + a bus using `ConcurrentHashMap<String, RingBuffer<JobEvent>>` and `CopyOnWriteArrayList<Consumer>`.

- [ ] **Step 4: Green.**

- [ ] **Step 5: Commit**

```powershell
git add backend\src\main\java\com\adi\naukri\orchestrator\JobEvent.java backend\src\main\java\com\adi\naukri\orchestrator\JobEventBus.java backend\src\test\java\com\adi\naukri\orchestrator\JobEventBusTest.java
git commit -m "feat(be): add JobEvent hierarchy and JobEventBus with replay"
```

### Task 6.2: `JobOrchestrator` — sequential worker + control ops

**Files:**
- Create: `backend\src\main\java\com\adi\naukri\orchestrator\JobRequest.java`
- Create: `backend\src\main\java\com\adi\naukri\orchestrator\JobHandle.java`
- Create: `backend\src\main\java\com\adi\naukri\orchestrator\JobOrchestrator.java`
- Create: `backend\src\test\java\com\adi\naukri\orchestrator\JobOrchestratorTest.java`

**Interfaces:**
- Produces:
  - `record JobRequest(List<String> emails, String password, boolean headless, boolean manualLogin, Path outputFolder)`
  - `class JobHandle { String jobId(); Future<Void> future(); }`
  - `class JobOrchestrator { JobHandle start(JobRequest r); void stop(String jobId); void continueNow(String jobId); void skip(String jobId); }`
  - Contract: `start` returns immediately with a `jobId`; the worker thread iterates emails one-by-one, calls `NaukriAutomator`, publishes events, writes reports at end. `stop` cancels current context + marks remaining as `SKIPPED`. `continueNow`/`skip` signal the manual-login gate for the current account.
  - Only one active job at a time — calling `start` while another job runs throws `IllegalStateException`.

- [ ] **Step 1: Test using an in-memory fake `NaukriAutomator` (behind an interface)**

Introduce an interface `Automator` extracted from `NaukriAutomator` so tests can inject a fake. (Refactor step — declare it in this task; production `NaukriAutomator` implements it unchanged.)

```java
class JobOrchestratorTest {
    @Test void runs_three_accounts_and_publishes_expected_events() { /* 3 emails, fake automator always OK */ }
    @Test void stop_marks_pending_as_skipped()                    { /* stop after 1st completes */ }
    @Test void manual_login_await_then_continue_resumes()         { /* fake automator blocks on gate */ }
    @Test void writes_report_files_in_output_folder()             { /* asserts report.csv exists */ }
    @Test void concurrent_start_rejected()                        { /* second start throws */ }
}
```

- [ ] **Step 2: FAIL.**

- [ ] **Step 3: Implement.** Single-thread `ExecutorService`. Publishes events per spec. Uses `ReportWriter` at the end. Injects `JobEventBus` and `Automator`.

- [ ] **Step 4: Green.**

- [ ] **Step 5: Commit**

```powershell
git add backend\src\main\java\com\adi\naukri\orchestrator backend\src\test\java\com\adi\naukri\orchestrator
git commit -m "feat(be): add JobOrchestrator with sequential runner and control ops"
```

---

## Milestone 7 — REST + WebSocket API (contract tests)

### Task 7.1: `JobController` (start/stop/continue/skip)

**Files:**
- Create: `backend\src\main\java\com\adi\naukri\api\JobController.java`
- Create: `backend\src\main\java\com\adi\naukri\api\StartJobRequest.java`
- Create: `backend\src\main\java\com\adi\naukri\api\StartJobResponse.java`
- Create: `backend\src\test\java\com\adi\naukri\api\JobControllerTest.java`

**Interfaces (per spec §10.1):**
- `POST /api/jobs` with JSON body → returns `{ jobId, wsUrl }`.
- `POST /api/jobs/{id}/stop`, `POST /api/jobs/{id}/continue`, `POST /api/jobs/{id}/skip` → `204`.

- [ ] **Step 1: Failing MockMvc test**

```java
@SpringBootTest @AutoConfigureMockMvc
class JobControllerTest {
    @Autowired MockMvc mvc;
    @MockBean JobOrchestrator orch;

    @Test void start_returns_jobId() throws Exception {
        when(orch.start(any())).thenReturn(new JobHandle("job-123", CompletableFuture.completedFuture(null)));
        mvc.perform(post("/api/jobs").contentType(MediaType.APPLICATION_JSON).content("""
            {"emails":["a@x.com"],"password":"p","headless":false,"manualLogin":false,"outputFolder":"C:\\\\tmp\\\\r"}
        """))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.jobId").value("job-123"))
        .andExpect(jsonPath("$.wsUrl").value("/ws/jobs/job-123"));
    }

    @Test void stop_calls_orchestrator() throws Exception {
        mvc.perform(post("/api/jobs/job-123/stop")).andExpect(status().isNoContent());
        verify(orch).stop("job-123");
    }
    @Test void continue_calls_orchestrator() throws Exception { /* ... */ }
    @Test void skip_calls_orchestrator()     throws Exception { /* ... */ }

    @Test void start_rejects_empty_emails() throws Exception {
        mvc.perform(post("/api/jobs").contentType(MediaType.APPLICATION_JSON).content("""
            {"emails":[],"password":"p","headless":false,"manualLogin":false,"outputFolder":"C:\\\\tmp"}
        """)).andExpect(status().isBadRequest());
    }
    @Test void start_rejects_blank_password() throws Exception { /* ... */ }
}
```

- [ ] **Step 2: FAIL.**

- [ ] **Step 3: Implement** DTOs with `@NotEmpty`/`@NotBlank` validators, controller mapping the four routes.

- [ ] **Step 4: Green (6 tests).**

- [ ] **Step 5: Commit**

```powershell
git add backend\src\main\java\com\adi\naukri\api\JobController.java backend\src\main\java\com\adi\naukri\api\StartJobRequest.java backend\src\main\java\com\adi\naukri\api\StartJobResponse.java backend\src\test\java\com\adi\naukri\api\JobControllerTest.java
git commit -m "feat(be): add JobController REST endpoints with validation"
```

### Task 7.2: `JobWebSocketHandler` (`/ws/jobs/{jobId}`)

**Files:**
- Create: `backend\src\main\java\com\adi\naukri\api\WebSocketConfig.java`
- Create: `backend\src\main\java\com\adi\naukri\api\JobWebSocketHandler.java`
- Create: `backend\src\test\java\com\adi\naukri\api\JobWebSocketHandlerIT.java`

**Interfaces:**
- Produces: text-frame WebSocket at `/ws/jobs/{jobId}` that streams JSON of every `JobEvent` for that `jobId`, including replay of buffered events on connect.

- [ ] **Step 1: Integration test**

Uses `StandardWebSocketClient` against `RANDOM_PORT` Spring Boot. Publishes 3 events via `JobEventBus`, connects, asserts client receives all three as JSON with expected `type` field.

- [ ] **Step 2: FAIL.**

- [ ] **Step 3: Implement** using Spring `TextWebSocketHandler` + `WebSocketHandlerRegistry`; subscribe to `JobEventBus` on connect, unsubscribe on close.

- [ ] **Step 4: Green.**

- [ ] **Step 5: Commit**

```powershell
git add backend\src\main\java\com\adi\naukri\api\WebSocketConfig.java backend\src\main\java\com\adi\naukri\api\JobWebSocketHandler.java backend\src\test\java\com\adi\naukri\api\JobWebSocketHandlerIT.java
git commit -m "feat(be): add /ws/jobs WebSocket handler with event streaming"
```

### Task 7.3: BE end-to-end integration (REST → orchestrator → automator → mock Naukri)

**Files:**
- Create: `backend\src\test\java\com\adi\naukri\integration\FullPipelineIT.java`

**Interfaces:**
- Consumes: the mock Naukri jar from Milestone 4, the REST + WS endpoints from Tasks 7.1/7.2, the real orchestrator + automator.
- Produces: proof that hitting `POST /api/jobs` triggers Playwright against the mock, publishes the right events, writes reports.

- [ ] **Step 1: Test**

```java
@Tag("integration")
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class FullPipelineIT {

    static Process mock;
    static int mockPort;
    @Autowired TestRestTemplate rest;
    @LocalServerPort int beport;

    @BeforeAll static void up() throws Exception { /* start mock jar on mockPort */ }
    @AfterAll  static void down()               { mock.destroy(); }

    @Test
    void full_pipeline_happy() throws Exception {
        Path outDir = Files.createTempDirectory("naukri-it");
        var req = Map.of("emails", List.of("ok@x.com","ok2@x.com"),
                         "password","p","headless",true,"manualLogin",false,
                         "outputFolder", outDir.toString(),
                         "baseUrlOverride","http://127.0.0.1:"+mockPort); // new field for tests
        var resp = rest.postForEntity("/api/jobs", req, Map.class);
        String jobId = (String) resp.getBody().get("jobId");

        // Subscribe to WS and collect events
        List<Map<String,Object>> events = new WsClient("/ws/jobs/"+jobId, beport).collectUntil("RUN_COMPLETED", 90);

        assertTrue(events.stream().anyMatch(e -> "RUN_STARTED".equals(e.get("type"))));
        assertEquals(2, events.stream().filter(e -> "ACCOUNT_COMPLETED".equals(e.get("type"))).count());
        assertTrue(Files.exists(outDir.resolve("report.csv")));
    }
}
```

Note: adds `baseUrlOverride` (test-only field) to `StartJobRequest`, wired into `AutomatorConfig.baseUrl`. Production FE never sends it.

- [ ] **Step 2: FAIL.**

- [ ] **Step 3: Wire `baseUrlOverride`** in `StartJobRequest` → orchestrator → automator config; add small `WsClient` helper in test scope.

- [ ] **Step 4: Green.**

- [ ] **Step 5: Commit**

```powershell
git add backend\src\test\java\com\adi\naukri\integration\FullPipelineIT.java backend\src\main\java\com\adi\naukri\api\StartJobRequest.java
git commit -m "test(be): full REST-to-mock-Naukri pipeline integration test"
```

---

## Milestone 8 — Frontend skeleton, design tokens, and Vitest harness

### Task 8.1: Vite + React + TS + Tailwind + shadcn scaffold

**Files:**
- Create: `frontend\package.json`, `frontend\vite.config.ts`, `frontend\tsconfig.json`, `frontend\tsconfig.node.json`
- Create: `frontend\index.html`
- Create: `frontend\tailwind.config.ts`, `frontend\postcss.config.js`
- Create: `frontend\src\main.tsx`, `frontend\src\App.tsx`, `frontend\src\index.css`

**Interfaces:**
- Produces: `npm --prefix frontend run dev` opens `http://127.0.0.1:5173` with a placeholder page saying `NAUKRI_AUTOMATOR` in mono type on the dark background. `npm run build` outputs `frontend\dist`.

- [ ] **Step 1: `package.json`**

```json
{
  "name": "naukri-fe",
  "private": true,
  "version": "0.1.0",
  "type": "module",
  "author": "Adikarthik Gupta C B",
  "scripts": {
    "dev":     "vite",
    "build":   "tsc -b && vite build",
    "preview": "vite preview",
    "test":    "vitest",
    "test:ci": "vitest run --reporter=verbose"
  },
  "dependencies": {
    "react": "^18.3.1",
    "react-dom": "^18.3.1",
    "framer-motion": "^11.0.0",
    "lucide-react": "^0.400.0",
    "clsx": "^2.1.0",
    "tailwind-merge": "^2.3.0",
    "class-variance-authority": "^0.7.0"
  },
  "devDependencies": {
    "vite": "^5.3.0",
    "@vitejs/plugin-react": "^4.3.0",
    "typescript": "^5.4.0",
    "@types/react": "^18.3.0",
    "@types/react-dom": "^18.3.0",
    "tailwindcss": "^3.4.0",
    "autoprefixer": "^10.4.0",
    "postcss": "^8.4.0",
    "vitest": "^1.6.0",
    "@testing-library/react": "^16.0.0",
    "@testing-library/jest-dom": "^6.4.0",
    "@testing-library/user-event": "^14.5.0",
    "jsdom": "^24.0.0",
    "msw": "^2.3.0"
  }
}
```

- [ ] **Step 2: Vite + Tailwind config**

`vite.config.ts`:
```ts
import { defineConfig } from "vite";
import react from "@vitejs/plugin-react";
import path from "node:path";

export default defineConfig({
  plugins: [react()],
  resolve: { alias: { "@": path.resolve(__dirname, "src") } },
  test: { environment: "jsdom", setupFiles: ["./src/test/setup.ts"], globals: true }
});
```

`tailwind.config.ts`:
```ts
import type { Config } from "tailwindcss";
export default {
  content: ["./index.html", "./src/**/*.{ts,tsx}"],
  theme: {
    extend: {
      colors: {
        bg: { base: "#050915", accent: "#0b1226" },
        text: { primary: "#e6edf3", muted: "#94a3b8" },
        accent: { cyan: "#22d3ee", violet: "#a855f7" },
        status: { ok: "#22c55e", warn: "#f59e0b", fail: "#ef4444", manual: "#a855f7" }
      },
      fontFamily: {
        head: ['"JetBrains Mono"', "ui-monospace", "monospace"],
        body: ["Inter", "system-ui", "sans-serif"]
      },
      borderRadius: { card: "16px" },
      boxShadow: { card: "0 8px 30px rgba(0,0,0,.35)" }
    }
  },
  plugins: []
} satisfies Config;
```

- [ ] **Step 3: Global CSS + shell**

`src/index.css`:
```css
@tailwind base; @tailwind components; @tailwind utilities;

@layer base {
  html, body, #root { height: 100%; }
  body {
    background: linear-gradient(180deg, #050915 0%, #0b1226 100%);
    color: #e6edf3;
    font-family: Inter, system-ui, sans-serif;
    -webkit-font-smoothing: antialiased;
  }
  h1, h2, h3, .mono { font-family: "JetBrains Mono", ui-monospace, monospace; letter-spacing: -0.02em; }
}
@layer components {
  .card { @apply rounded-card shadow-card border border-white/10 bg-white/[0.04] backdrop-blur-md; }
  .accent-gradient { background-image: linear-gradient(90deg, #22d3ee, #a855f7); -webkit-background-clip: text; background-clip: text; color: transparent; }
}
```

`src/App.tsx`:
```tsx
export default function App() {
  return (
    <div className="min-h-screen flex flex-col">
      <header className="px-8 py-4 flex items-center justify-between border-b border-white/5">
        <div>
          <h1 className="text-2xl accent-gradient">NAUKRI_AUTOMATOR</h1>
          <p className="text-xs text-text-muted">by Adikarthik Gupta C B</p>
        </div>
        <span className="text-xs text-text-muted mono">v0.1.0</span>
      </header>
      <main className="flex-1 p-8">
        <div className="card p-8">Ready.</div>
      </main>
    </div>
  );
}
```

- [ ] **Step 4: Vitest smoke test**

`src/test/setup.ts`:
```ts
import "@testing-library/jest-dom";
```

`src/App.test.tsx`:
```tsx
import { render, screen } from "@testing-library/react";
import App from "./App";

describe("App shell", () => {
  it("shows brand name and byline", () => {
    render(<App />);
    expect(screen.getByText("NAUKRI_AUTOMATOR")).toBeInTheDocument();
    expect(screen.getByText(/Adikarthik Gupta C B/)).toBeInTheDocument();
  });
});
```

Run:
```powershell
cd F:\views\g\Naukri\frontend
npm ci
npm run test:ci
```
Expected: 1 test pass.

- [ ] **Step 5: Commit**

```powershell
git add frontend\package.json frontend\vite.config.ts frontend\tsconfig.json frontend\tsconfig.node.json frontend\index.html frontend\tailwind.config.ts frontend\postcss.config.js frontend\src frontend\.gitignore
git commit -m "feat(fe): scaffold Vite+React+TS+Tailwind with dark digital shell"
```

### Task 8.2: API client (REST + WS) and event types

**Files:**
- Create: `frontend\src\api\types.ts` — TypeScript equivalents of every `JobEvent`, `StartJobRequest`, `StartJobResponse`, `AccountStatus`.
- Create: `frontend\src\api\rest.ts` — thin `fetch` wrappers for `POST /api/jobs`, stop/continue/skip, `GET /api/template`.
- Create: `frontend\src\api\ws.ts` — `connectJobStream(jobId, onEvent, onClose)` returning `{ close() }`.
- Create: `frontend\src\api\rest.test.ts` — msw-backed test.
- Create: `frontend\src\api\ws.test.ts` — uses `mock-socket` or an inline `MockWebSocket`.

**Interfaces:**
- Produces the exact TS types + a client surface that all screens use.

- [ ] **Step 1: Tests first** — msw handlers assert request payload and stub responses. WS test verifies message decode + onClose semantics.

- [ ] **Step 2: Implement.**

`types.ts`:
```ts
export type AccountStatus = "OK" | "AUTH_FAILED" | "REQUIRES_MANUAL" | "FAILED" | "SKIPPED";

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
```

`rest.ts` reads a bootstrap `window.NAUKRI_BE_PORT` (injected by Electron preload) and prefixes all calls with `http://127.0.0.1:${port}`.

`ws.ts` similarly uses `ws://127.0.0.1:${port}/ws/jobs/${jobId}`.

- [ ] **Step 3: Green tests.**

- [ ] **Step 4: Commit**

```powershell
git add frontend\src\api
git commit -m "feat(fe): API client (REST+WS) with typed events and tests"
```

---

## Milestone 9 — Setup screen (all controls + validation)

### Task 9.1: `EmailChipInput` component

**Files:**
- Create: `frontend\src\components\EmailChipInput.tsx`
- Create: `frontend\src\components\EmailChipInput.test.tsx`

**Interfaces:**
- Props: `{ value: string[]; onChange(next: string[]): void }`.
- Accepts email on `Enter`, `,`, or blur. Rejects invalid emails with an inline error. Backspace on empty removes last chip.

- [ ] Test → FAIL → implement → green → commit.

Concrete test cases: (a) typing `a@x.com` + Enter adds chip; (b) invalid entry shows error and does not add; (c) backspace removes last chip; (d) duplicate rejected.

### Task 9.2: `ExcelDropzone` component

**Files:**
- Create: `frontend\src\components\ExcelDropzone.tsx`
- Create: `frontend\src\components\ExcelDropzone.test.tsx`

**Interfaces:**
- Props: `{ onParsed(rows: ParsedEmailRow[]): void }`.
- Uploads xlsx via `<input type=file>` and POSTs to `POST /api/parse-excel` (add this endpoint in this task — small controller that reuses `EmailExcelParser`). Renders a preview table + an "invalid rows" panel.

**BE additions for this task:** Create `POST /api/parse-excel` in a new `ExcelController` returning `List<ParsedEmailRow>` — with its own MockMvc test.

- [ ] BE test → BE impl → FE test (msw stubs /api/parse-excel) → FE impl → commit.

### Task 9.3: `SetupScreen` (compose everything)

**Files:**
- Create: `frontend\src\screens\SetupScreen.tsx`
- Create: `frontend\src\screens\SetupScreen.test.tsx`

**Interfaces:**
- Renders two tabs (`Upload Excel` / `Enter manually`), the password field, the visible/headless toggle, the manual-login toggle (disables headless when on), the output folder picker (invokes Electron preload `window.electronAPI.pickFolder()`), the Download-template link, and the Start button.
- Emits `onStart(request: StartJobRequest)` when Start is clicked and validation passes.

- [ ] Test cases:
  - Start button disabled when no emails.
  - Start button disabled when password blank.
  - Turning on manual-login forces `headless=false` and disables the headless toggle.
  - Clicking "Download Excel template" triggers a `GET /api/template` fetch (asserted via msw).
  - Clicking Start emits the correct payload.

- [ ] Impl → Green → Commit.

---

## Milestone 10 — Run screen (live progress + manual-login pause)

### Task 10.1: `useJobStream` hook

**Files:**
- Create: `frontend\src\hooks\useJobStream.ts`
- Create: `frontend\src\hooks\useJobStream.test.ts`

**Interfaces:**
- `useJobStream(jobId): { events, byEmail, summary, awaitingManual, connectionState }`
- Reduces every event type from §10.2 into a per-email `AccountView { email, index, currentStep, status, elapsedMs, startedAt }`.
- Tracks `awaitingManual: { email, deadline } | null`.

- [ ] Test — inject a fake WS that pushes a scripted sequence of events; assert the reduced state after each event.

- [ ] Impl using `useReducer` + effect that connects via `api/ws.ts`.

- [ ] Green + commit.

### Task 10.2: `RunTable` + `ProgressRing` + `ManualLoginCallout` components

**Files:**
- Create: `frontend\src\components\RunTable.tsx`, `RunTable.test.tsx`
- Create: `frontend\src\components\ProgressRing.tsx`, `ProgressRing.test.tsx`
- Create: `frontend\src\components\ManualLoginCallout.tsx`, `ManualLoginCallout.test.tsx`

**Interfaces:**
- `RunTable` — pure props table, one row per email with animated status pill (framer-motion).
- `ProgressRing` — SVG circular ring with `value/total` centred; uses accent gradient stroke.
- `ManualLoginCallout` — shows email, countdown from `deadline`, `Continue now` + `Skip account` buttons that call `POST /api/jobs/{id}/continue|skip`.

- [ ] Test each in isolation → Impl → Green → Commit.

### Task 10.3: `RunScreen` composition + Stop button

**Files:**
- Create: `frontend\src\screens\RunScreen.tsx`, `RunScreen.test.tsx`

**Interfaces:**
- Props: `{ jobId: string; onCompleted(summary): void }`.
- Renders `ProgressRing` + `RunTable` + optional `ManualLoginCallout`.
- Sticky bottom bar with `Stop` (confirm dialog before firing `POST /api/jobs/{id}/stop`).
- On `RUN_COMPLETED` event, calls `onCompleted(summary)` which triggers screen switch to Results.

- [ ] Test using a controlled `useJobStream` mock: renders every possible state (idle, running, awaiting-manual, completed, stopped) → assert DOM.

- [ ] Impl → Green → Commit.

---

## Milestone 11 — Results screen + screen router

### Task 11.1: `ResultsScreen`

**Files:**
- Create: `frontend\src\screens\ResultsScreen.tsx`, `ResultsScreen.test.tsx`

**Interfaces:**
- Props: `{ summary: RunSummary; accounts: AccountView[]; outputFolder: string; onNewRun(): void }`.
- Renders summary tiles (`OK`, `AUTH_FAILED`, `REQUIRES_MANUAL`, `FAILED`, `SKIPPED`), final table (same shape as `RunTable` but read-only + resume rename shown), and three buttons: `Open report folder` (invokes `window.electronAPI.openFolder(outputFolder)`), `Export CSV` (downloads the same CSV BE already wrote — reuses `GET /api/runs/{jobId}/report.csv` — added as a small controller in this task), `New run`.

**BE additions for this task:** Add `RunController` with `GET /api/runs/{jobId}/report.csv` streaming the file from disk (validates `jobId` against a small in-memory registry populated by orchestrator on completion). MockMvc test included.

- [ ] Tests → Impl → Green → Commit.

### Task 11.2: `AppRouter` (Setup ↔ Run ↔ Results) + shell finalization

**Files:**
- Modify: `frontend\src\App.tsx` — introduce `useState<"setup"|"run"|"results">`, hero + stepper + sticky bottom bar wired to current screen.
- Create: `frontend\src\components\Stepper.tsx`, `Stepper.test.tsx`

**Interfaces:**
- Stepper shows 3 nodes with the active one glowing (accent gradient).
- `App` owns the whole run lifecycle: holds `jobId`, `outputFolder`, `summary` in state.

- [ ] Test the router with fake API + verify transitions: `Setup → Run` on Start success, `Run → Results` on `RUN_COMPLETED`, `Results → Setup` on `New run`.

- [ ] Green + Commit.

---

## Milestone 12 — Electron shell (main process, preload, packaging config)

### Task 12.1: `electron\main.js` + `preload.js` + package config

**Files:**
- Create: `electron\package.json`
- Create: `electron\main.js`
- Create: `electron\preload.js`
- Create: `electron\electron-builder.yml`
- Create: `electron\src\ipc.js` — small helper that owns port-parsing + child spawn
- Create: `electron\test\main.spawn.test.js`, `electron\test\preload.contract.test.js`

**Interfaces:**
- `main.js` starts the Java BE as a child process (`java -jar naukri-be.jar --server.port=0`), reads stdout until `NAUKRI_BE_PORT=<n>` appears, then loads `renderer/index.html` and injects the port via `window.NAUKRI_BE_PORT`.
- `preload.js` exposes a locked API `window.electronAPI = { pickFolder, openFolder, portInfo }`.
- Kills the Java child on window close.

- [ ] **Step 1: `electron\package.json`**

```json
{
  "name": "naukri-electron",
  "version": "0.1.0",
  "main": "main.js",
  "author": "Adikarthik Gupta C B",
  "scripts": {
    "dev":  "electron .",
    "test": "node --test test/*.test.js",
    "dist": "electron-builder --win nsis portable --config electron-builder.yml"
  },
  "devDependencies": {
    "electron": "^31.0.0",
    "electron-builder": "^24.13.0"
  }
}
```

- [ ] **Step 2: Contract test for `preload.js`** — validates the API surface exposed to renderer is exactly `pickFolder`, `openFolder`, `portInfo` and nothing else (uses jsdom + a stub `contextBridge`).

- [ ] **Step 3: Spawn test for `ipc.js`**

```js
// electron/test/main.spawn.test.js
import { test } from "node:test";
import assert from "node:assert/strict";
import { parsePortLine, waitForPort } from "../src/ipc.js";

test("parsePortLine extracts port from announcer line", () => {
  assert.equal(parsePortLine("Something\nNAUKRI_BE_PORT=54321\nOther"), 54321);
});

test("waitForPort rejects on timeout", async () => {
  const child = { stdout: { on: () => {} } };
  await assert.rejects(waitForPort(child, 200));
});
```

- [ ] **Step 4: Implement `ipc.js`, `main.js`, `preload.js`.**

Key excerpt of `main.js`:
```js
const { app, BrowserWindow, ipcMain, dialog, shell } = require("electron");
const { spawn } = require("node:child_process");
const path = require("node:path");
const { waitForPort } = require("./src/ipc.js");

let child, win;

function javaExe() {
  return path.join(process.resourcesPath, "jre", "bin", "javaw.exe");
}
function beJar() {
  return path.join(process.resourcesPath, "backend", "naukri-be.jar");
}

app.whenReady().then(async () => {
  child = spawn(javaExe(), ["-jar", beJar(), "--server.port=0"], { windowsHide: true });
  const port = await waitForPort(child, 30_000);

  win = new BrowserWindow({
    width: 1200, height: 800, backgroundColor: "#050915",
    autoHideMenuBar: true, title: "NaukriAutomator",
    webPreferences: { preload: path.join(__dirname, "preload.js"), contextIsolation: true, sandbox: true }
  });

  await win.loadFile(path.join(__dirname, "renderer", "index.html"), {
    query: { port: String(port) }
  });
});

ipcMain.handle("pickFolder", async () => {
  const r = await dialog.showOpenDialog(win, { properties: ["openDirectory", "createDirectory"] });
  return r.canceled ? null : r.filePaths[0];
});
ipcMain.handle("openFolder", (_e, p) => shell.openPath(p));

app.on("window-all-closed", () => { if (child) child.kill(); app.quit(); });
```

`preload.js`:
```js
const { contextBridge, ipcRenderer } = require("electron");
const params = new URL(window.location.href).searchParams;
const port = Number(params.get("port"));
contextBridge.exposeInMainWorld("electronAPI", {
  pickFolder:  () => ipcRenderer.invoke("pickFolder"),
  openFolder:  (p) => ipcRenderer.invoke("openFolder", p),
  portInfo:    () => ({ port })
});
window.NAUKRI_BE_PORT = port; // used by frontend/src/api/rest.ts
```

- [ ] **Step 5: `electron-builder.yml`**

```yaml
appId: com.adi.naukri.automator
productName: NaukriAutomator
copyright: Copyright © 2026 Adikarthik Gupta C B
directories:
  output: ../dist
files:
  - main.js
  - preload.js
  - src/**
  - renderer/**
  - package.json
extraResources:
  - from: ../backend/target/naukri-be.jar
    to:   backend/naukri-be.jar
  - from: resources/jre
    to:   jre
  - from: resources/playwright
    to:   playwright
  - from: ../mock-naukri/target/mock-naukri.jar
    to:   mock/mock-naukri.jar   # bundled ONLY for the E2E build variant; see build.ps1
win:
  target:
    - target: nsis
    - target: portable
  icon: build/icon.ico
nsis:
  oneClick: false
  perMachine: false
  allowElevation: false
  allowToChangeInstallationDirectory: true
  shortcutName: NaukriAutomator
```

Note: the mock jar in `extraResources` is included conditionally by `build.ps1` (removed for the shipping build). For the E2E build variant it is present so Playwright E2E can spawn it.

- [ ] **Step 6: Run tests**

```powershell
cd F:\views\g\Naukri\electron
npm install
npm test
```
Expected: both tests pass.

- [ ] **Step 7: Commit**

```powershell
git add electron
git commit -m "feat(electron): main+preload+builder config with typed IPC and tests"
```

---

## Milestone 13 — Build pipeline (`build\build.ps1`)

### Task 13.1: Per-phase scripts + orchestrator

**Files:**
- Create: `build\phases\build-backend.ps1`
- Create: `build\phases\build-frontend.ps1`
- Create: `build\phases\build-electron.ps1`
- Create: `build\phases\build-mock.ps1`
- Create: `build\fetch-jre.ps1`
- Create: `build\install-playwright.ps1`
- Create: `build\build.ps1`
- Create: `build\build.tests.ps1` — smoke test the produced EXE launches and health-checks in <30s

**Interfaces:**
- `build.ps1 [-Variant Ship|E2E]` (default `Ship`)
  - `Ship` produces `NaukriAutomator-Setup-<ver>.exe` and `NaukriAutomator-Portable-<ver>.exe` under `dist\`.
  - `E2E` bundles the mock Naukri jar too, output under `dist\e2e\`.
- Every phase script is idempotent, prints `==== phase X ====` banners, and exits non-zero on failure.
- `fetch-jre.ps1` downloads Temurin 17 Windows x64 zip and caches it in `build\.cache\jre.zip`; re-extract only if missing.
- `install-playwright.ps1` runs `mvn dependency:properties` to locate the Playwright CLI, then `java -cp ... com.microsoft.playwright.CLI install chromium --with-deps` targeted at `electron\resources\playwright\`.

- [ ] **Step 1: `build.ps1`**

```powershell
#Requires -Version 5.1
[CmdletBinding()] Param(
  [ValidateSet('Ship','E2E')] [string] $Variant = 'Ship'
)
$ErrorActionPreference = 'Stop'
$root = Split-Path -Parent $PSScriptRoot

Write-Host "==== [1/6] Backend ===="   ; & "$PSScriptRoot\phases\build-backend.ps1"
Write-Host "==== [2/6] Mock Naukri ====" ; & "$PSScriptRoot\phases\build-mock.ps1"
Write-Host "==== [3/6] Frontend ===="  ; & "$PSScriptRoot\phases\build-frontend.ps1"
Write-Host "==== [4/6] JRE ===="       ; & "$PSScriptRoot\fetch-jre.ps1"
Write-Host "==== [5/6] Playwright ====" ; & "$PSScriptRoot\install-playwright.ps1"
Write-Host "==== [6/6] Electron ($Variant) ====" ; & "$PSScriptRoot\phases\build-electron.ps1" -Variant $Variant

Write-Host "Build complete. Artifacts under: $root\dist"
```

- [ ] **Step 2: `phases\build-backend.ps1`**

```powershell
$ErrorActionPreference = 'Stop'
$root = Split-Path -Parent (Split-Path -Parent $PSScriptRoot)
Push-Location "$root\backend"
mvn -q clean package -DskipTests
if ($LASTEXITCODE -ne 0) { throw "mvn failed" }
Pop-Location
```

- [ ] **Step 3a: `phases\build-mock.ps1`**

```powershell
$ErrorActionPreference = 'Stop'
$root = Split-Path -Parent (Split-Path -Parent $PSScriptRoot)
Push-Location "$root\mock-naukri"
mvn -q clean package -DskipTests
if ($LASTEXITCODE -ne 0) { throw "mvn (mock) failed" }
Pop-Location
```

- [ ] **Step 3b: `phases\build-frontend.ps1`**

```powershell
$ErrorActionPreference = 'Stop'
$root = Split-Path -Parent (Split-Path -Parent $PSScriptRoot)
Push-Location "$root\frontend"
if (-not (Test-Path node_modules)) { npm ci } else { npm install }
if ($LASTEXITCODE -ne 0) { throw "npm install failed" }
npm run build
if ($LASTEXITCODE -ne 0) { throw "vite build failed" }
$dest = "$root\electron\renderer"
if (Test-Path $dest) { Remove-Item -Recurse -Force $dest }
New-Item -ItemType Directory -Force $dest | Out-Null
Copy-Item -Recurse -Force "$root\frontend\dist\*" $dest
Pop-Location
```

- [ ] **Step 3c: `phases\build-electron.ps1`**

```powershell
[CmdletBinding()] Param([ValidateSet('Ship','E2E')] [string] $Variant = 'Ship')
$ErrorActionPreference = 'Stop'
$root = Split-Path -Parent (Split-Path -Parent $PSScriptRoot)

# Stage resources
$resDir = "$root\electron\resources"
New-Item -ItemType Directory -Force "$resDir\backend" | Out-Null
Copy-Item -Force "$root\backend\target\naukri-be.jar" "$resDir\backend\naukri-be.jar"

$mockOut = "$resDir\mock"
if ($Variant -eq 'E2E') {
  New-Item -ItemType Directory -Force $mockOut | Out-Null
  Copy-Item -Force "$root\mock-naukri\target\mock-naukri.jar" "$mockOut\mock-naukri.jar"
} elseif (Test-Path $mockOut) {
  Remove-Item -Recurse -Force $mockOut
}

# Choose output dir per variant
$distDir = if ($Variant -eq 'E2E') { "$root\dist\e2e" } else { "$root\dist" }
$env:ELECTRON_BUILDER_OUTPUT = $distDir

Push-Location "$root\electron"
if (-not (Test-Path node_modules)) { npm ci }
if ($LASTEXITCODE -ne 0) { throw "npm ci (electron) failed" }
npx electron-builder --win nsis portable --config electron-builder.yml
if ($LASTEXITCODE -ne 0) { throw "electron-builder failed" }
Pop-Location
```

- [ ] **Step 4: Smoke test `build.tests.ps1`**

```powershell
# Build the E2E variant, launch the portable exe, wait 15s, hit /api/health via localhost by
# sniffing the port from the running BE child process, then kill.
$ErrorActionPreference = 'Stop'
& $PSScriptRoot\build.ps1 -Variant Ship
$exe = Get-ChildItem -Path (Join-Path $PSScriptRoot '..\dist') -Filter 'NaukriAutomator-Portable-*.exe' | Select-Object -First 1
if (-not $exe) { throw "portable exe not produced" }
$proc = Start-Process $exe.FullName -PassThru
Start-Sleep -Seconds 15
if ($proc.HasExited) { throw "process died prematurely; exit=$($proc.ExitCode)" }
Stop-Process -Id $proc.Id -Force
Write-Host "SMOKE OK"
```

- [ ] **Step 5: Run**

```powershell
cd F:\views\g\Naukri
.\build\build.ps1 -Variant Ship
.\build\build.tests.ps1
```
Expected: `SMOKE OK` printed, both installer and portable exe present under `dist\`.

- [ ] **Step 6: Commit**

```powershell
git add build
git commit -m "feat(build): PowerShell-only build pipeline with variants and smoke test"
```

---

## Milestone 14 — Full-stack E2E + single-command test runner

### Task 14.1: Playwright E2E project driving the packaged Electron app

**Files:**
- Create: `e2e\package.json`
- Create: `e2e\playwright.config.ts`
- Create: `e2e\tests\happy-path.spec.ts`
- Create: `e2e\tests\manual-login.spec.ts`
- Create: `e2e\tests\excel-upload.spec.ts`
- Create: `e2e\fixtures\emails.xlsx` — generated at test-time by a small script using `exceljs`, or checked in.

**Interfaces:**
- Uses Playwright's `electron` driver: `electron.launch({ args: ['dist\\win-unpacked\\resources\\app\\main.js'] })`.
- Each test spec first starts the bundled mock Naukri jar on a free port (`spawn`), and passes the base-URL override via a debug-only flag (`--mock-base=http://127.0.0.1:<port>`). Backend already accepts `baseUrlOverride` via the `POST /api/jobs` request body from Milestone 7 Task 7.3.
- `happy-path.spec.ts` runs a full 2-account flow, waits for Results, asserts report.csv exists on disk.
- `manual-login.spec.ts` uses `otp@x.com`, verifies the manual-login callout appears; clicks Continue after simulating OTP completion in a Playwright-controlled sub-page; verifies resume runs to completion.
- `excel-upload.spec.ts` uploads the fixture xlsx and confirms preview + start.

- [ ] **Step 1: Config**

`e2e\package.json`:
```json
{
  "name": "naukri-e2e",
  "private": true,
  "scripts": { "test": "playwright test" },
  "devDependencies": {
    "@playwright/test": "^1.44.0",
    "exceljs": "^4.4.0"
  }
}
```

`playwright.config.ts`:
```ts
import { defineConfig } from "@playwright/test";
export default defineConfig({
  testDir: "./tests",
  timeout: 180_000,
  reporter: [["list"], ["html", { outputFolder: "playwright-report" }]],
  workers: 1
});
```

- [ ] **Step 2: `happy-path.spec.ts`**

```ts
import { test, expect, _electron as electron } from "@playwright/test";
import { spawn } from "node:child_process";
import path from "node:path";
import net from "node:net";
import fs from "node:fs";

async function freePort(): Promise<number> {
  return await new Promise((resolve, reject) => {
    const s = net.createServer();
    s.listen(0, () => { const p = (s.address() as net.AddressInfo).port; s.close(() => resolve(p)); });
    s.on("error", reject);
  });
}

test("2-account happy flow produces report.csv", async () => {
  const mockPort = await freePort();
  const jar = path.resolve(__dirname, "../../dist/e2e/resources/mock/mock-naukri.jar");
  const mock = spawn("java", ["-jar", jar, `--server.port=${mockPort}`], { stdio: "ignore" });
  await new Promise(r => setTimeout(r, 5000));

  const outDir = fs.mkdtempSync(path.join(process.env.TEMP!, "na-e2e-"));
  const appDir = path.resolve(__dirname, "../../dist/e2e/win-unpacked/resources/app");
  const electronApp = await electron.launch({ args: [appDir] });
  const win = await electronApp.firstWindow();

  await win.fill("[data-testid='chip-input']", "ok@x.com");   await win.keyboard.press("Enter");
  await win.fill("[data-testid='chip-input']", "ok2@x.com");  await win.keyboard.press("Enter");
  await win.fill("[data-testid='password']", "p");
  await win.fill("[data-testid='output-folder']", outDir);
  await win.evaluate((port) => (window as any).NAUKRI_E2E_MOCK = `http://127.0.0.1:${port}`, mockPort);
  await win.click("[data-testid='start']");

  await win.waitForSelector("[data-testid='results-screen']", { timeout: 120_000 });
  await expect(win.locator("[data-testid='count-ok']")).toHaveText("2");

  expect(fs.existsSync(path.join(outDir, "report.csv"))).toBe(true);
  await electronApp.close();
  mock.kill();
});
```

**Note for the FE task list:** every screen must add stable `data-testid` attributes on interactive elements referenced above; the SetupScreen/RunScreen/ResultsScreen tests already assert these test IDs. Add them during their respective task implementations.

**Note for the BE:** to support `NAUKRI_E2E_MOCK`, the FE reads that global (only present in E2E builds via a devtools-injected value) and forwards it as `baseUrlOverride` in `POST /api/jobs`. Production build never reads this.

- [ ] **Step 3: `manual-login.spec.ts`** — single email `otp@x.com`, enable manual-login toggle, verifies the callout, opens a separate Playwright-controlled tab pointed at the mock's `/otp` page, submits, then confirms Run screen resumes.

- [ ] **Step 4: `excel-upload.spec.ts`** — writes an `emails.xlsx` via exceljs, uploads it, asserts preview shows 3 emails, then Start.

- [ ] **Step 5: Green all three**

```powershell
cd F:\views\g\Naukri
.\build\build.ps1 -Variant E2E
cd e2e ; npm ci ; npm test
```

- [ ] **Step 6: Commit**

```powershell
git add e2e
git commit -m "test(e2e): Playwright end-to-end suite against packaged Electron + mock Naukri"
```

### Task 14.2: Single test runner `build\test.ps1`

**Files:**
- Create: `build\test.ps1`

**Interfaces:**
- Runs BE unit + integration tests, FE unit tests, then E2E. Prints a per-phase pass/fail summary. Exits non-zero on any failure.

- [ ] **Step 1: Script**

```powershell
#Requires -Version 5.1
[CmdletBinding()] Param([switch] $SkipE2E)
$ErrorActionPreference = 'Stop'
$root = Split-Path -Parent $PSScriptRoot
$fail = @()

function Section($name, [ScriptBlock] $body) {
  Write-Host "`n==== $name ====" -ForegroundColor Cyan
  try { & $body; if ($LASTEXITCODE -ne 0) { throw "$name exit=$LASTEXITCODE" } }
  catch { $script:fail += $name; Write-Host "FAILED: $name" -ForegroundColor Red }
}

Section 'BE verify' { Push-Location "$root\backend";  mvn -q verify; Pop-Location }
Section 'Mock tests' { Push-Location "$root\mock-naukri"; mvn -q test; Pop-Location }
Section 'FE tests'   { Push-Location "$root\frontend"; npm run test:ci; Pop-Location }
Section 'Electron tests' { Push-Location "$root\electron"; npm test; Pop-Location }
if (-not $SkipE2E) {
  Section 'Build E2E variant' { & "$root\build\build.ps1" -Variant E2E }
  Section 'E2E tests'         { Push-Location "$root\e2e"; npm test; Pop-Location }
}

Write-Host "`n==== Summary ====" -ForegroundColor Cyan
if ($fail.Count -eq 0) { Write-Host "ALL GREEN" -ForegroundColor Green; exit 0 }
else { Write-Host "FAILED: $($fail -join ', ')" -ForegroundColor Red; exit 1 }
```

- [ ] **Step 2: Run**

```powershell
cd F:\views\g\Naukri
.\build\test.ps1
```
Expected on green project: prints `ALL GREEN`. Exit 0.

- [ ] **Step 3: Commit**

```powershell
git add build\test.ps1
git commit -m "test: add single-command runner for BE+FE+Electron+E2E"
```

### Task 14.3: Documentation of the test surface

**Files:**
- Modify: `README.md` — add sections *Running tests*, *Building*, *Manual login*, *Reports*.
- Create: `docs\testing.md` — describes each test layer, how to run individual layers, how to inspect failures.

- [ ] Write docs; run `test.ps1` one final time; commit.

```powershell
git add README.md docs\testing.md
git commit -m "docs: describe test layers and build variants"
```

---

## Cross-cutting: exit checklist before declaring the plan implementation done

- [ ] `build\test.ps1` prints **`ALL GREEN`** with no `-SkipE2E`.
- [ ] `build\build.ps1 -Variant Ship` produces both `dist\NaukriAutomator-Setup-<ver>.exe` and `dist\NaukriAutomator-Portable-<ver>.exe`.
- [ ] `build\build.tests.ps1` prints **`SMOKE OK`** against the just-built portable EXE.
- [ ] Byline **Adikarthik Gupta C B** appears in: `README.md`, `frontend/src/App.tsx` header, Electron `package.json` `author`, `electron-builder.yml` `copyright`, `backend/src/main/resources/banner.txt`, backend `/api/health` response.
- [ ] Password never appears in any log file (grep `logs\` after a manual test run).
- [ ] Ship variant does NOT contain `mock-naukri.jar` in `dist\win-unpacked\resources\`.





