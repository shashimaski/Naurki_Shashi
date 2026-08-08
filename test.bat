@echo off
REM ============================================================================
REM  test.bat  -  Windows-friendly wrapper around build\test.ps1
REM
REM  Runs the full test suite: BE + Mock + FE + Electron + E2E.
REM
REM  Usage:
REM     test.bat                Full run (all layers)
REM     test.bat -SkipE2E       Everything except E2E
REM
REM  Author: Adikarthik Gupta C B
REM ============================================================================
setlocal
set "SCRIPT_DIR=%~dp0"
powershell.exe -NoProfile -ExecutionPolicy Bypass -File "%SCRIPT_DIR%build\test.ps1" %*
set "EXITCODE=%ERRORLEVEL%"
echo.
if %EXITCODE% EQU 0 (
    echo [test.bat] ALL TESTS GREEN
) else (
    echo [test.bat] TESTS FAILED with exit code %EXITCODE%.
)
pause
endlocal & exit /b %EXITCODE%
