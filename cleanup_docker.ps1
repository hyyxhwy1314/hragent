$paths = @(
    'C:\Users\Administrator\AppData\Local\Docker\wsl_bakROBO',
    'C:\Users\Administrator\AppData\Local\Docker\wsl_C_BACKUP_DELETE_LATER',
    'C:\Users\Administrator\AppData\Local\Docker\wsl_C_NEW_EMPTY_DELETEME'
)

foreach ($p in $paths) {
    if (Test-Path $p) {
        $size = (Get-ChildItem $p -Recurse -File -ErrorAction SilentlyContinue | Measure-Object -Property Length -Sum).Sum
        Remove-Item $p -Recurse -Force -ErrorAction SilentlyContinue
        if (-not (Test-Path $p)) {
            Write-Host ("DELETED: " + $p + " (was " + [math]::Round($size/1GB,2) + " GB)")
        } else {
            Write-Host ("FAILED to delete: " + $p)
        }
    } else {
        Write-Host ("Not exists: " + $p)
    }
}

Write-Host ""
Write-Host "=== Remaining files in C:\Users\Administrator\AppData\Local\Docker ==="
$total = (Get-ChildItem 'C:\Users\Administrator\AppData\Local\Docker' -Recurse -File -ErrorAction SilentlyContinue | Measure-Object -Property Length -Sum).Sum
Write-Host ("Total size: " + [math]::Round($total/1GB,2) + " GB")
Get-ChildItem 'C:\Users\Administrator\AppData\Local\Docker' | ForEach-Object { Write-Host $_.Name }
