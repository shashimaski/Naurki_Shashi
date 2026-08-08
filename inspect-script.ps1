# Read bytes of build.tests.ps1 and find non-ASCII chars
$srcPath = 'F:\views\g\Naukri\build\build.tests.ps1'
$bytes = [System.IO.File]::ReadAllBytes($srcPath)
Write-Host "File size: $($bytes.Length) bytes"
Write-Host "First 10 bytes: $($bytes[0..9] -join ',')"

# Find non-ASCII bytes and their positions
for ($i = 0; $i -lt $bytes.Length; $i++) {
    if ($bytes[$i] -gt 127) {
        # find surrounding context
        $start = [Math]::Max(0, $i-10)
        $end   = [Math]::Min($bytes.Length-1, $i+10)
        $ctx = $bytes[$start..$end] -join ','
        Write-Host "Non-ASCII byte 0x$($bytes[$i].ToString('X2')) at offset $i | context bytes: $ctx"
    }
}
