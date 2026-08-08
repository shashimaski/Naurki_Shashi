$env:JAVA_HOME = 'C:\Users\e182114\.jdks\azul-17.0.10'
$env:PATH = $env:JAVA_HOME + '\bin;' + $env:PATH
$job = Start-Job -ScriptBlock {
    $env:JAVA_HOME = 'C:\Users\e182114\.jdks\azul-17.0.10'
    $env:PATH = $env:JAVA_HOME + '\bin;' + $env:PATH
    & "$env:JAVA_HOME\bin\java" -jar 'F:\views\g\Naukri\backend\target\naukri-be.jar' 2>&1
}
Start-Sleep -Seconds 15
$out = Receive-Job -Job $job
$portLine = $out | Select-String "NAUKRI_BE_PORT="
Write-Output "=== PORT ANNOUNCER OUTPUT ==="
Write-Output $portLine
Stop-Job $job
Remove-Job $job
