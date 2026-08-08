param([string]$Dir, [string[]]$MvnArgs)
$env:JAVA_HOME = 'C:\Users\e182114\.jdks\azul-17.0.10'
$env:PATH = $env:JAVA_HOME + '\bin;' + $env:PATH
Set-Location $Dir
& mvn @MvnArgs
exit $LASTEXITCODE
