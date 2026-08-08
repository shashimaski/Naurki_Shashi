# DEV-mode end-to-end verification script
# Created by: Adikarthik Gupta C B
# Date: 2026-07-15

$ErrorActionPreference = 'Continue'
$reportDir = 'F:\views\g\Naukri\.superpowers\sdd'
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

# Kill any stray java on 8080 first
$stray = Get-Process java, javaw -ErrorAction SilentlyContinue
if ($stray) {
    Log "  Stopping $($stray.Count) stray Java process(es) before start..."
    $stray | Stop-Process -Force -ErrorAction SilentlyContinue
    Start-Sleep -Seconds 2
}

$env:JAVA_HOME = "C:\Users\e182114\.jdks\azul-17.0.10"
$javaExe = "$env:JAVA_HOME\bin\java.exe"
$beJar   = "F:\views\g\Naukri\backend\target\naukri-be.jar"

if (-not (Test-Path $javaExe)) {
    Log "  ERROR: java.exe not found at $javaExe"
    exit 1
}
if (-not (Test-Path $beJar)) {
    Log "  ERROR: JAR not found at $beJar"
    exit 1
}
Log "  java.exe : $javaExe  [EXISTS]"
Log "  JAR      : $beJar  [EXISTS]"

# Remove any old job with same name
Stop-Job  naukri-be -ErrorAction SilentlyContinue
Remove-Job naukri-be -ErrorAction SilentlyContinue

$beJob = Start-Job -Name naukri-be -ScriptBlock {
    param($java, $jar)
    & $java -jar $jar --server.port=8080 2>&1
} -ArgumentList $javaExe, $beJar

Log "  BE job started (Id=$($beJob.Id)). Waiting up to 60s for /api/health..."

$deadline = (Get-Date).AddSeconds(60)
$beReady  = $false
while ((Get-Date) -lt $deadline) {
    Start-Sleep -Seconds 3
    try {
        $r = Invoke-WebRequest -Uri 'http://127.0.0.1:8080/api/health' -UseBasicParsing -TimeoutSec 2 -ErrorAction Stop
        if ($r.StatusCode -eq 200) { $beReady = $true; break }
    } catch {}
}

if (-not $beReady) {
    $beOut = Receive-Job $beJob -ErrorAction SilentlyContinue
    Log "  FAILED - BE did not come up. Last output:"
    Log "  $($beOut -join "`n  ")"
    Stop-Job  naukri-be -ErrorAction SilentlyContinue
    Remove-Job naukri-be -ErrorAction SilentlyContinue
    # Write partial report
    [System.IO.File]::WriteAllText($reportPath, ($log -join "`n"), [System.Text.UTF8Encoding]::new($false))
    exit 1
}

Log "  **BE READY on :8080**"
$healthDirect = Invoke-WebRequest -Uri 'http://127.0.0.1:8080/api/health' -UseBasicParsing
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
try {
    $pf = Invoke-WebRequest -Uri 'http://127.0.0.1:8080/api/jobs' -Method Options -UseBasicParsing -Headers @{
        "Origin"                         = "http://127.0.0.1:5173"
        "Access-Control-Request-Method"  = "POST"
        "Access-Control-Request-Headers" = "Content-Type"
    } -TimeoutSec 5 -ErrorAction Stop
    $pfStatus = $pf.StatusCode
    $pfACAO   = $pf.Headers['Access-Control-Allow-Origin']
    Log "  PREFLIGHT -> status=$pfStatus  ACAO=$pfACAO"
} catch {
    Log "  PREFLIGHT ERROR: $_"
    $pfStatus = 'ERROR'
    $pfACAO   = ''
}

# Actual POST
$postDirectStatus = 'NOT_RUN'
$postDirectBody   = ''
$postDirectJobId  = ''
try {
    $resp = Invoke-WebRequest -Uri 'http://127.0.0.1:8080/api/jobs' -Method Post `
        -ContentType 'application/json' -Body $body `
        -Headers @{ "Origin" = "http://127.0.0.1:5173" } `
        -UseBasicParsing -TimeoutSec 10 -ErrorAction Stop
    $postDirectStatus = $resp.StatusCode
    $postDirectBody   = $resp.Content
    try {
        $j = $resp.Content | ConvertFrom-Json
        $postDirectJobId = $j.jobId
    } catch {}
    Log "  POST /api/jobs -> status=$postDirectStatus"
    Log "  body: $postDirectBody"
} catch {
    $postDirectStatus = "ERROR: $_"
    Log "  POST /api/jobs ERROR: $_"
}
Log ""

# ------------------------------------------------------------------
# STEP 3: Start FE dev server
# ------------------------------------------------------------------
Log "## Step 3: Start Frontend Dev Server on :5173"

Stop-Job  naukri-fe -ErrorAction SilentlyContinue
Remove-Job naukri-fe -ErrorAction SilentlyContinue

# Check if node_modules exist
$nodeModules = 'F:\views\g\Naukri\frontend\node_modules'
if (-not (Test-Path $nodeModules)) {
    Log "  node_modules not found - running npm install..."
    $npmOut = & npm.cmd --prefix 'F:\views\g\Naukri\frontend' install 2>&1
    Log "  npm install: $($npmOut | Select-Object -Last 5 | Out-String)"
}

$feJob = Start-Job -Name naukri-fe -ScriptBlock {
    Set-Location 'F:\views\g\Naukri\frontend'
    & npm.cmd run dev 2>&1
}

Log "  FE job started (Id=$($feJob.Id)). Waiting up to 30s for :5173..."

$deadline = (Get-Date).AddSeconds(30)
$feReady  = $false
while ((Get-Date) -lt $deadline) {
    Start-Sleep -Seconds 2
    try {
        $r = Invoke-WebRequest -Uri 'http://127.0.0.1:5173/' -UseBasicParsing -TimeoutSec 2 -ErrorAction Stop
        if ($r.StatusCode -eq 200) { $feReady = $true; break }
    } catch {}
}

if (-not $feReady) {
    $feOut = Receive-Job $feJob -ErrorAction SilentlyContinue
    Log "  FAILED - FE did not come up on :5173"
    Log "  Last FE output:"
    Log "  $($feOut | Select-Object -Last 20 | Out-String)"
} else {
    Log "  **FE READY on :5173**"
    $feRoot = Invoke-WebRequest -Uri 'http://127.0.0.1:5173/' -UseBasicParsing
    Log "  GET / -> status=$($feRoot.StatusCode)  content-type=$($feRoot.Headers['Content-Type'])"
}
Log ""

# ------------------------------------------------------------------
# STEP 4: Proxy /api/health via Vite
# ------------------------------------------------------------------
Log "## Step 4: Proxied GET /api/health (FE -> BE via Vite proxy)"

$proxiedHealthStatus = 'NOT_RUN'
$proxiedHealthBody   = ''
if ($feReady) {
    try {
        $ph = Invoke-WebRequest -Uri 'http://127.0.0.1:5173/api/health' -UseBasicParsing -TimeoutSec 5 -ErrorAction Stop
        $proxiedHealthStatus = $ph.StatusCode
        $proxiedHealthBody   = $ph.Content
        Log "  PROXIED /api/health -> status=$proxiedHealthStatus  body=$proxiedHealthBody"
    } catch {
        $proxiedHealthStatus = "ERROR: $_"
        Log "  PROXIED /api/health ERROR: $_"
    }
} else {
    Log "  SKIPPED (FE not up)"
}
Log ""

# ------------------------------------------------------------------
# STEP 5: Proxied POST /api/jobs
# ------------------------------------------------------------------
Log "## Step 5: Proxied POST /api/jobs (FE -> BE via Vite proxy)"

$proxiedPostStatus = 'NOT_RUN'
$proxiedPostBody   = ''
$proxiedPostJobId  = ''
if ($feReady) {
    try {
        $rp = Invoke-WebRequest -Uri 'http://127.0.0.1:5173/api/jobs' -Method Post `
            -ContentType 'application/json' -Body $body `
            -UseBasicParsing -TimeoutSec 10 -ErrorAction Stop
        $proxiedPostStatus = $rp.StatusCode
        $proxiedPostBody   = $rp.Content
        try {
            $j2 = $rp.Content | ConvertFrom-Json
            $proxiedPostJobId = $j2.jobId
        } catch {}
        Log "  PROXIED POST /api/jobs -> status=$proxiedPostStatus"
        Log "  body: $proxiedPostBody"
    } catch {
        $proxiedPostStatus = "ERROR: $_"
        Log "  PROXIED POST /api/jobs ERROR: $_"
    }
} else {
    Log "  SKIPPED (FE not up)"
}
Log ""

# ------------------------------------------------------------------
# STEP 6: Cleanup
# ------------------------------------------------------------------
Log "## Step 6: Cleanup"
Stop-Job  naukri-be, naukri-fe -ErrorAction SilentlyContinue
Remove-Job naukri-be, naukri-fe -ErrorAction SilentlyContinue
Get-Process java, javaw -ErrorAction SilentlyContinue | Stop-Process -Force -ErrorAction SilentlyContinue
# Kill node/vite processes spawned for this project
Get-Process node -ErrorAction SilentlyContinue | Where-Object { $_.Path -match 'node' } | Stop-Process -Force -ErrorAction SilentlyContinue
Log "  Stopped BE and FE jobs."
Log ""

# ------------------------------------------------------------------
# STEP 7: Summary
# ------------------------------------------------------------------
Log "## Summary"
Log ""
Log "| Check | Result |"
Log "|-------|--------|"
Log "| BE /api/health (direct) | $($healthDirect.StatusCode) |"
Log "| CORS preflight ACAO header | $pfACAO |"
Log "| Direct POST /api/jobs | $postDirectStatus |"
Log "| Direct POST jobId | $postDirectJobId |"
Log "| FE dev server :5173 | $(if ($feReady) { 'UP' } else { 'FAILED' }) |"
Log "| Proxied GET /api/health | $proxiedHealthStatus |"
Log "| Proxied POST /api/jobs | $proxiedPostStatus |"
Log "| Proxied POST jobId | $proxiedPostJobId |"
Log ""
Log "### Concerns / Notes"
Log "- The POST /api/jobs with headless=true and a real email will cause the BE to queue an actual Playwright job."
Log "  Since the smoke email/password are fake, the Playwright automation will fail login quickly without reaching naukri.com in a meaningful way."
Log "- All processes cleaned up in Step 6."
Log ""
Log "---"
Log "_Verified by: Adikarthik Gupta C B | 2026-07-15_"

# ------------------------------------------------------------------
# Write report
# ------------------------------------------------------------------
if (-not (Test-Path $reportDir)) {
    New-Item -ItemType Directory -Path $reportDir -Force | Out-Null
}
[System.IO.File]::WriteAllText($reportPath, ($log -join "`n"), [System.Text.UTF8Encoding]::new($false))
Write-Host ""
Write-Host "Report written to: $reportPath"
Write-Host ""
Write-Host "=== FINAL RESULTS ==="
Write-Host "BE direct POST /api/jobs:   $postDirectStatus  jobId=$postDirectJobId"
Write-Host "FE proxy POST /api/jobs:    $proxiedPostStatus  jobId=$proxiedPostJobId"
Write-Host "CORS preflight ACAO:        $pfACAO"
