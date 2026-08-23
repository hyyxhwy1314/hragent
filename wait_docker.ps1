$max = 180
$ok = $false
while ($max -gt 0 -and -not $ok) {
    Start-Sleep -Seconds 5
    $max -= 5
    try {
        $r = docker info --format '{{.ServerVersion}}' 2>&1
        if ($LASTEXITCODE -eq 0 -and $r) {
            Write-Host ("READY: " + $r)
            $ok = $true
        } else {
            Write-Host ("waiting... " + $max + "s left")
        }
    } catch {
        Write-Host ("err: " + $_)
    }
}
if (-not $ok) { Write-Host "TIMEOUT" }
