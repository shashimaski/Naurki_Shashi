Set-Location 'F:\views\g\Naukri\frontend'
Write-Host "Building frontend..."
$npmCmd = "C:\Program Files\nodejs\npm.cmd"
& $npmCmd run build
if ($LASTEXITCODE -ne 0) { throw "FE build failed with exit code $LASTEXITCODE" }

# Verify relative paths
$ihtml = 'F:\views\g\Naukri\frontend\dist\index.html'
if (Select-String -Path $ihtml -Pattern 'src="/|href="/' -Quiet) { throw "absolute paths in built HTML -- vite base='./' regression" }

# Verify CSS selectors
$css = Get-ChildItem 'F:\views\g\Naukri\frontend\dist\assets' -Filter '*.css' | Select-Object -First 1
if (-not $css) { throw "No CSS file found in dist/assets" }
Write-Host "Checking CSS file: $($css.FullName)"
$missing = @('\.btn-primary', '\.input', '-webkit-autofill', '--accent') | Where-Object { -not (Select-String -Path $css.FullName -Pattern $_ -Quiet) }
if ($missing) { throw "missing CSS selectors in built bundle: $($missing -join ', ')" }

Write-Host "Step 3: FE build SUCCESS -- index.html has relative paths, CSS has all required selectors"
Write-Host "CSS file: $($css.FullName)  size=$([math]::Round($css.Length/1KB,1))KB"
