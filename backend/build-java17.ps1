$env:JAVA_HOME = 'C:\Users\e182114\.jdks\azul-17.0.10'
$env:PATH = $env:JAVA_HOME + '\bin;' + $env:PATH
Set-Location 'F:\views\g\Naukri\backend'
Write-Output "Java version:"
& "$env:JAVA_HOME\bin\java" -version 2>&1
Write-Output "Building..."
& mvn '-q' 'clean' 'package' '-DskipTests'
Write-Output "Build exit code: $LASTEXITCODE"
