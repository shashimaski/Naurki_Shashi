#Requires -Version 5.1
<#
.SYNOPSIS
    Downloads Temurin 17 Windows x64 JRE and extracts to electron/resources/jre/.
    Idempotent - no-op if javaw.exe already present.
    Created by Adikarthik Gupta C B
#>
$ErrorActionPreference = 'Stop'
Set-StrictMode -Version Latest

Write-Host '==== fetch-jre ===='

$root       = Split-Path -Parent $PSScriptRoot
$jreDestDir = Join-Path $root 'electron\resources\jre'
$javawExe   = Join-Path $jreDestDir 'bin\javaw.exe'

if (Test-Path $javawExe) {
    Write-Host "JRE already present at $jreDestDir - skipping download."
    Write-Host '==== fetch-jre DONE (cached) ===='
    exit 0
}

$cacheDir = Join-Path $PSScriptRoot '.cache'
if (-not (Test-Path $cacheDir)) { New-Item -ItemType Directory -Force -Path $cacheDir | Out-Null }

$jreZip = Join-Path $cacheDir 'jre.zip'
$downloadUrl = 'https://api.adoptium.net/v3/binary/latest/17/ga/windows/x64/jre/hotspot/normal/eclipse'

Write-Host "Downloading Temurin 17 JRE from: $downloadUrl"
Write-Host "Target zip : $jreZip"

# Use WebClient for PS 5.1 compatibility (Invoke-WebRequest can be slow on large files)
$wc = New-Object System.Net.WebClient
$wc.DownloadFile($downloadUrl, $jreZip)
Write-Host "Download complete: $('{0:N1}' -f ((Get-Item $jreZip).Length / 1MB)) MB"

Write-Host "Extracting to $jreDestDir ..."
if (-not (Test-Path $jreDestDir)) { New-Item -ItemType Directory -Force -Path $jreDestDir | Out-Null }

Add-Type -AssemblyName System.IO.Compression.FileSystem
$zip = [System.IO.Compression.ZipFile]::OpenRead($jreZip)
foreach ($entry in $zip.Entries) {
    # Strip the top-level directory (e.g. "jdk-17.0.x-jre/") from the entry path
    $parts     = $entry.FullName -split '/', 2
    $relPath   = if ($parts.Count -gt 1) { $parts[1] } else { $parts[0] }
    if ([string]::IsNullOrEmpty($relPath)) { continue }

    $destPath = Join-Path $jreDestDir ($relPath -replace '/', '\')
    if ($entry.FullName.EndsWith('/')) {
        if (-not (Test-Path $destPath)) { New-Item -ItemType Directory -Force -Path $destPath | Out-Null }
    } else {
        $destDir = Split-Path -Parent $destPath
        if (-not (Test-Path $destDir)) { New-Item -ItemType Directory -Force -Path $destDir | Out-Null }
        [System.IO.Compression.ZipFileExtensions]::ExtractToFile($entry, $destPath, $true)
    }
}
$zip.Dispose()

if (-not (Test-Path $javawExe)) {
    throw "fetch-jre: extraction completed but javaw.exe not found at $javawExe"
}

Write-Host "JRE installed at $jreDestDir"
Write-Host '==== fetch-jre DONE ===='
