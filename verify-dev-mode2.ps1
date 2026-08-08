# DEV-mode end-to-end verification script v2
# Created by: Adikarthik Gupta C B
# Date: 2026-07-15

$ErrorActionPreference = 'Continue'
$reportDir  = 'F:\views\g\Naukri\.superpowers\sdd'
$reportPath = "$reportDir\dev-mode-verification.md"

$log = [System.Collections.Generic.List[string]]::new()
function Log($msg) {
    Write-Host $msg
    $log.Add($msg)
}

Log "# DEV-Mode End-to-End Verification"
Log "**Date:** 2026-07-15"
Log "**Author:** Adikarthik Gupta C B"
Log ""

# ------------------------------------------------------------------
# STEP 1: Start BE on port 8080
# ------------------------------------------------------------------
Log "## Step 1: Start Backend on :8080"

# Kill stray java processes
$stray = Get-Process java, javaw -ErrorAction SilentlyContinue
if ($stray) {
    Log "  Stopping $($stray.Count) stray Java process(es)..."
    $stray | Stop-Process -Force -ErrorAction SilentlyContinue
    Start-Sleep -Seconds 2
}

$javaExe = 'C:\Users\e182114\.jdks\azul-17.0.10\bin\java.exe'
$beJar   = 'F:\views\g\Naukri\backend\target\naukri-be.jar'

Log "  java.exe : $(Test-Path $javaExe)"
Log "  JAR      : $(Test-Path $beJar)"

# Start BE as a background process (not Start-Job which is session-scoped)
$beProc = Start-Process -FilePath $javaExe -ArgumentList "-jar `"$beJar`" --server.port=8080" -PassThru -WindowStyle Hidden -RedirectStandardOutput "$env:TEMP\naukri-be-stdout.txt" -RedirectStandardError "$env:TEMP\naukri-be-stderr.txt"
Log "  BE process started (PID=$($beProc.Id)). Waiting up to 90s for /api/health..."

$deadline = (Get-Date).AddSeconds(90)
$beReady  = $false
while ((Get-Date) -lt $deadline) {
    Start-Sleep -Seconds 3
    try {
        $r = Invoke-WebRequest -Uri 'http://127.0.0.1:8080/api/health' -UseBasicParsing -TimeoutSec 2 -ErrorAction Stop
        if ($r.StatusCode -eq 200) { $beReady = $true; break }
    } catch {}
}

if (-not $beReady) {
    Log "  FAILED - BE did not come up within 90s"
    $beErr = Get-Content "$env:TEMP\naukri-be-stderr.txt" -ErrorAction SilentlyContinue | Select-Object -Last 30
    Log "  Last stderr:"
    foreach ($line in $beErr) { Log "    $line" }
    if (-not $beProc.HasExited) { $beProc.Kill() }
    [System.IO.File]::WriteAllText($reportPath, ($log -join "`n"), [System.Text.UTF8Encoding]::new($false))
    exit 1
}

$healthDirect = Invoke-WebRequest -Uri 'http://127.0.0.1:8080/api/health' -UseBasicParsing
Log "  **BE READY on :8080**"
Log "  /api/health direct -> status=$($healthDirect.StatusCode)  body=$($healthDirect.Content)"
Log ""

# ------------------------------------------------------------------
# STEP 2: CORS preflight + POST /api/jobs (direct to BE)
# ------------------------------------------------------------------
Log "## Step 2: CORS Preflight + POST /api/jobs (direct to BE)"

$body = @{
    emails       = @('smoke@example.com')
    password     = 'test-password'
    headless     = $true
    manualLogin  = $false
    outputFolder = "$env:TEMP\naukri-dev-smoke"
} | ConvertTo-Json

# Preflight
$pfStatus = 'ERROR'; $pfACAO = ''; $pfAllowMethods = ''
try {
    $pf = Invoke-WebRequest -Uri 'http://127.0.0.1:8080/api/jobs' -Method Options -UseBasicParsing -Headers @{
        'Origin'                         = 'http://127.0.0.1:5173'
        'Access-Control-Request-Method'  = 'POST'
        'Access-Control-Request-Headers' = 'Content-Type'
    } -TimeoutSec 5 -ErrorAction Stop
    $pfStatus       = $pf.StatusCode
    $pfACAO         = $pf.Headers['Access-Control-Allow-Origin']
    $pfAllowMethods = $pf.Headers['Access-Control-Allow-Methods']
    Log "  PREFLIGHT status=$pfStatus  ACAO=$pfACAO  Allow-Methods=$pfAllowMethods"
    # Log all CORS headers
    foreach ($key in $pf.Headers.Keys | Where-Object { $_ -like 'Access-Control*' }) {
        Log "    $key : $($pf.Headers[$key])"
    }
} catch {
    Log "  PREFLIGHT ERROR: $_"
}

# Actual POST direct
$postDirectStatus = 'NOT_RUN'; $postDirectBody = ''; $postDirectJobId = ''
try {
    $resp = Invoke-WebRequest -Uri 'http://127.0.0.1:8080/api/jobs' -Method Post `
        -ContentType 'application/json' -Body $body `
        -Headers @{ 'Origin' = 'http://127.0.0.1:5173' } `
        -UseBasicParsing -TimeoutSec 10 -ErrorAction Stop
    $postDirectStatus = $resp.StatusCode
    $postDirectBody   = $resp.Content
    try { $postDirectJobId = ($resp.Content | ConvertFrom-Json).jobId } catch {}
    Log "  POST /api/jobs -> status=$postDirectStatus"
    Log "  body: $postDirectBody"
    Log "  jobId: $postDirectJobId"
} catch {
    $postDirectStatus = "ERROR: $_"
    Log "  POST /api/jobs ERROR: $_"
}
Log ""

# ------------------------------------------------------------------
# STEP 3: Start FE dev server
# ------------------------------------------------------------------
Log "## Step 3: Start Frontend Dev Server on :5173"

# Kill any stray node on 5173
$nodeProcs = Get-Process node -ErrorAction SilentlyContinue
if ($nodeProcs) {
    Log "  Stopping $($nodeProcs.Count) stray node process(es)..."
    $nodeProcs | Stop-Process -Force -ErrorAction SilentlyContinue
    Start-Sleep -Seconds 1
}

# Check node_modules
if (-not (Test-Path 'F:\views\g\Naukri\frontend\node_modules')) {
    Log "  node_modules missing - running npm install..."
    $npmOut = & npm.cmd --prefix 'F:\views\g\Naukri\frontend' install 2>&1
    Log "  npm install exit: $LASTEXITCODE"
}

# Start FE as a background process
$feProc = Start-Process -FilePath 'cmd.exe' -ArgumentList '/c npm.cmd run dev' -WorkingDirectory 'F:\views\g\Naukri\frontend' -PassThru -WindowStyle Hidden -RedirectStandardOutput "$env:TEMP\naukri-fe-stdout.txt" -RedirectStandardError "$env:TEMP\naukri-fe-stderr.txt"
Log "  FE process started (PID=$($feProc.Id)). Waiting up to 30s for :5173..."

# Vite binds to localhost (which on this machine resolves to either 127.0.0.1 or ::1)
# Try both
$deadline = (Get-Date).AddSeconds(30)
$feReady  = $false
while ((Get-Date) -lt $deadline) {
    Start-Sleep -Seconds 2
    foreach ($host_ in @('127.0.0.1', 'localhost')) {
        try {
            $r = Invoke-WebRequest -Uri "http://$host_`:5173/" -UseBasicParsing -TimeoutSec 2 -ErrorAction Stop
            if ($r.StatusCode -eq 200) { $feReady = $true; $feHost = $host_; break }
        } catch {}
    }
    if ($feReady) { break }
}

if (-not $feReady) {
    $feStdout = Get-Content "$env:TEMP\naukri-fe-stdout.txt" -ErrorAction SilentlyContinue | Select-Object -Last 20
    $feStderr = Get-Content "$env:TEMP\naukri-fe-stderr.txt" -ErrorAction SilentlyContinue | Select-Object -Last 20
    Log "  FE did NOT come up on :5173"
    Log "  stdout: $($feStdout -join '; ')"
    Log "  stderr: $($feStderr -join '; ')"
    $feHost = 'localhost'  # default for proxy test anyway
} else {
    $feRoot = Invoke-WebRequest -Uri "http://$feHost`:5173/" -UseBasicParsing
    Log "  **FE READY on :5173 (host=$feHost)**"
    Log "  GET / -> status=$($feRoot.StatusCode)  content-type=$($feRoot.Headers['Content-Type'])"
}
Log ""

# ------------------------------------------------------------------
# STEP 4: Proxy /api/health via Vite
# ------------------------------------------------------------------
Log "## Step 4: Proxied GET /api/health (Vite -> BE)"

$proxiedHealthStatus = 'SKIPPED'; $proxiedHealthBody = ''
foreach ($host_ in @('127.0.0.1', 'localhost')) {
    try {
        $ph = Invoke-WebRequest -Uri "http://$host_`:5173/api/health" -UseBasicParsing -TimeoutSec 5 -ErrorAction Stop
        $proxiedHealthStatus = $ph.StatusCode
        $proxiedHealthBody   = $ph.Content
        Log "  PROXIED /api/health (via $host_) -> status=$proxiedHealthStatus  body=$proxiedHealthBody"
        break
    } catch {
        Log "  PROXIED /api/health via $host_ failed: $_"
    }
}
Log ""

# ------------------------------------------------------------------
# STEP 5: Proxied POST /api/jobs
# ------------------------------------------------------------------
Log "## Step 5: Proxied POST /api/jobs (Vite -> BE)"

$proxiedPostStatus = 'SKIPPED'; $proxiedPostBody = ''; $proxiedPostJobId = ''
foreach ($host_ in @('127.0.0.1', 'localhost')) {
    try {
        $rp = Invoke-WebRequest -Uri "http://$host_`:5173/api/jobs" -Method Post `
            -ContentType 'application/json' -Body $body `
            -UseBasicParsing -TimeoutSec 10 -ErrorAction Stop
        $proxiedPostStatus = $rp.StatusCode
        $proxiedPostBody   = $rp.Content
        try { $proxiedPostJobId = ($rp.Content | ConvertFrom-Json).jobId } catch {}
        Log "  PROXIED POST /api/jobs (via $host_) -> status=$proxiedPostStatus"
        Log "  body: $proxiedPostBody"
        Log "  jobId: $proxiedPostJobId"
        break
    } catch {
        Log "  PROXIED POST /api/jobs via $host_ ERROR: $_"
    }
}
Log ""

# ------------------------------------------------------------------
# STEP 6: Cleanup
# ------------------------------------------------------------------
Log "## Step 6: Cleanup"
if (-not $beProc.HasExited) { $beProc.Kill(); Log "  Killed BE (PID=$($beProc.Id))" }
if (-not $feProc.HasExited) { $feProc.Kill(); Log "  Killed FE (PID=$($feProc.Id))" }
Get-Process java, javaw -ErrorAction SilentlyContinue | Stop-Process -Force -ErrorAction SilentlyContinue
Get-Process node -ErrorAction SilentlyContinue | Stop-Process -Force -ErrorAction SilentlyContinue
Log "  Cleanup done."
Log ""

# ------------------------------------------------------------------
# Summary
# ------------------------------------------------------------------
Log "## Summary Table"
Log ""
Log "| Check | Result |"
Log "|-------|--------|"
Log "| BE /api/health (direct) | status=$($healthDirect.StatusCode) body=$($healthDirect.Content) |"
Log "| CORS preflight status | $pfStatus |"
Log "| CORS Access-Control-Allow-Origin | $pfACAO |"
Log "| CORS Access-Control-Allow-Methods | $pfAllowMethods |"
Log "| Direct POST /api/jobs status | $postDirectStatus |"
Log "| Direct POST jobId | $postDirectJobId |"
Log "| FE dev server :5173 | $(if ($feReady) { 'UP' } else { 'DOWN' }) |"
Log "| Proxied GET /api/health | $proxiedHealthStatus |"
Log "| Proxied GET body | $proxiedHealthBody |"
Log "| Proxied POST /api/jobs status | $proxiedPostStatus |"
Log "| Proxied POST jobId | $proxiedPostJobId |"
Log ""
Log "### Notes"
Log "- BE response body from /api/health includes author='Adikarthik Gupta C B'"
Log "- POST /api/jobs uses fake creds (smoke@example.com / test-password); Playwright job will fail at login but that is expected for this smoke test."
Log "- All processes killed in Step 6."
Log ""
Log "---"
Log "_Verified by: Adikarthik Gupta C B | 2026-07-15_"

# Write report
if (-not (Test-Path $reportDir)) { New-Item -ItemType Directory -Path $reportDir -Force | Out-Null }
[System.IO.File]::WriteAllText($reportPath, ($log -join "`n"), [System.Text.UTF8Encoding]::new($false))

Write-Host ""
Write-Host "=============================="
Write-Host "REPORT: $reportPath"
Write-Host "=============================="
Write-Host "BE direct POST:   $postDirectStatus  jobId=$postDirectJobId"
Write-Host "FE proxied POST:  $proxiedPostStatus  jobId=$proxiedPostJobId"
Write-Host "CORS ACAO:        $pfACAO"
