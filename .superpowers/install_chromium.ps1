$env:JAVA_HOME = 'C:\Users\e182114\.jdks\azul-17.0.10'
$env:PATH = $env:JAVA_HOME + '\bin;' + $env:PATH
Set-Location 'F:\views\g\Naukri\backend'
Write-Host "Installing Playwright Chromium..."
mvn -q exec:java '-Dexec.mainClass=com.microsoft.playwright.CLI' '-Dexec.args=install chromium' 2>&1
Write-Host "Install exit: $LASTEXITCODE"
exit $LASTEXITCODE
