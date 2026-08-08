Set-Location 'F:\views\g\Naukri\frontend'
Write-Host 'Starting FE build...'
& 'C:\Program Files\nodejs\npm.cmd' run build
if ($LASTEXITCODE -ne 0) { throw "FE build failed with exit code $LASTEXITCODE" }
Write-Host 'FE build succeeded'

# Verify relative paths in built HTML
$ihtml = 'F:\views\g\Naukri\frontend\dist\index.html'
$absPathMatch = Select-String -Path $ihtml -Pattern 'src="/|href="/' -Quiet
if ($absPathMatch) { throw "FAIL: absolute paths found in built HTML -- vite base='./' regression" }
Write-Host "HTML check PASS: no absolute asset paths"

# Verify new CSS selectors present
$cssFiles = Get-ChildItem 'F:\views\g\Naukri\frontend\dist\assets' -Filter '*.css'
if ($cssFiles.Count -eq 0) { throw "No CSS files found in dist/assets" }
$css = $cssFiles | Select-Object -First 1
Write-Host "Checking CSS: $($css.FullName)"
$patterns = @('\.btn-primary', '\.input', '-webkit-autofill', '--accent')
$missing = $patterns | Where-Object { -not (Select-String -Path $css.FullName -Pattern $_ -Quiet) }
if ($missing) { throw "FAIL: missing CSS selectors in built bundle: $($missing -join ', ')" }
Write-Host "CSS check PASS: all selectors present"

# Report dist size
$distSize = (Get-ChildItem 'F:\views\g\Naukri\frontend\dist' -Recurse -File | Measure-Object -Property Length -Sum).Sum
$distMB = [math]::Round($distSize / 1MB, 1)
Write-Host "FE dist total size: $distMB MB"
