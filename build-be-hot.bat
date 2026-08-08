@echo off
REM ============================================================================
REM  build-be-hot.bat  -  Backend-only hotpatch wrapper
REM
REM  Compiles just the Spring Boot fat JAR (~10s, no frontend or electron
REM  packaging) and drops it at dist\hotpatch\naukri-be.jar so you can copy
REM  a single file to an already-installed app for fast iteration on
REM  pure-Java changes (automation, selectors, orchestrator, REST, retry).
REM
REM  Copy the produced jar to:
REM     %%LOCALAPPDATA%%\Programs\NaukriAutomator\resources\backend\naukri-be.jar
REM  and relaunch the app.
REM
REM  NOT valid for frontend or Electron main / preload changes -- those still
REM  need the full build.bat.
REM
REM  Author: Adikarthik Gupta C B
REM ============================================================================
setlocal
set "SCRIPT_DIR=%~dp0"
powershell.exe -NoProfile -ExecutionPolicy Bypass -File "%SCRIPT_DIR%build\phases\build-backend-only.ps1" %*
set "EXITCODE=%ERRORLEVEL%"
echo.
if %EXITCODE% EQU 0 (
    echo [build-be-hot.bat] HOTPATCH JAR at dist\hotpatch\naukri-be.jar
) else (
    echo [build-be-hot.bat] HOTPATCH FAILED with exit code %EXITCODE%.
)
pause
endlocal & exit /b %EXITCODE%
