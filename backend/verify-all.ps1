$env:JAVA_HOME = 'C:\Users\e182114\.jdks\azul-17.0.10'
$env:PATH = $env:JAVA_HOME + '\bin;' + $env:PATH
Set-Location 'F:\views\g\Naukri\backend'

Write-Output "=== VERIFICATION 1: Build JAR ==="
& mvn '-q' 'clean' 'package' '-DskipTests'
Write-Output "mvn exit code: $LASTEXITCODE"

Write-Output ""
Write-Output "=== VERIFICATION 2: Confirm JAR exists ==="
$jarExists = Test-Path 'target\naukri-be.jar'
Write-Output "Test-Path target\naukri-be.jar : $jarExists"

Write-Output ""
Write-Output "=== VERIFICATION 3: Port announcer ==="
$job = Start-Job -ScriptBlock {
    $env:JAVA_HOME = 'C:\Users\e182114\.jdks\azul-17.0.10'
    $env:PATH = $env:JAVA_HOME + '\bin;' + $env:PATH
    & "$env:JAVA_HOME\bin\java" -jar 'F:\views\g\Naukri\backend\target\naukri-be.jar' 2>&1
}
Start-Sleep -Seconds 15
$out = Receive-Job -Job $job
$portLine = $out | Select-String "NAUKRI_BE_PORT="
Write-Output "Port announcer line:"
Write-Output $portLine
Stop-Job $job
Remove-Job $job

Write-Output ""
Write-Output "=== VERIFICATION 4: Unit test green ==="
& mvn 'test' '-Dtest=HealthControllerTest' '-q' 2>&1
Write-Output "Test exit code: $LASTEXITCODE"
