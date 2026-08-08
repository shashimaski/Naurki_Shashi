@echo off
REM ============================================================================
REM  build.bat  -  Windows-friendly wrapper around build\build.ps1
REM
REM  Bypasses PowerShell execution policy so you can either double-click this
REM  file OR run it from cmd.exe without touching your Windows security config.
REM
REM  Usage:
REM     build.bat                    Ship variant (installer + portable; default)
REM     build.bat -Variant E2E       includes the mock JAR for E2E tests
REM
REM  Output:
REM     dist\NaukriAutomator Setup 0.1.0.exe    NSIS installer (~396 MB)
REM     dist\NaukriAutomator 0.1.0.exe          Portable (~396 MB)
REM
REM  Author: Adikarthik Gupta C B
REM ============================================================================
setlocal
set "SCRIPT_DIR=%~dp0"
powershell.exe -NoProfile -ExecutionPolicy Bypass -File "%SCRIPT_DIR%build\build.ps1" %*
set "EXITCODE=%ERRORLEVEL%"
echo.
if %EXITCODE% EQU 0 (
    echo [build.bat] BUILD SUCCESS - see dist\ for output.
) else (
    echo [build.bat] BUILD FAILED with exit code %EXITCODE%.
)
pause
endlocal & exit /b %EXITCODE%
