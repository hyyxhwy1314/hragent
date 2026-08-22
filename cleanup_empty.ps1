$paths = @(
    'C:\Users\Administrator\AppData\Local\Docker\wsl_bakROBO',
    'C:\Users\Administrator\AppData\Local\Docker\wsl_C_BACKUP_DELETE_LATER',
    'C:\Users\Administrator\AppData\Local\Docker\wsl_C_NEW_EMPTY_DELETEME'
)

foreach ($p in $paths) {
    if (Test-Path $p) {
        # Try removing empty subdirectories first
        Get-ChildItem $p -Directory -Recurse -ErrorAction SilentlyContinue | Sort-Object FullName -Descending | ForEach-Object {
            Remove-Item $_.FullName -Force -ErrorAction SilentlyContinue
        }
        Remove-Item $p -Force -Recurse -ErrorAction SilentlyContinue
        if (-not (Test-Path $p)) {
            Write-Host ("DELETED: " + $p)
        } else {
            Write-Host ("STILL EXISTS (empty shell): " + $p)
        }
    } else {
        Write-Host ("Not exists: " + $p)
    }
}

Write-Host ""
Write-Host "=== Final state of C:\Users\Administrator\AppData\Local\Docker ==="
$total = (Get-ChildItem 'C:\Users\Administrator\AppData\Local\Docker' -Recurse -File -ErrorAction SilentlyContinue | Measure-Object -Property Length -Sum).Sum
Write-Host ("Total size on C: " + [math]::Round($total/1GB,2) + " GB")
Get-ChildItem 'C:\Users\Administrator\AppData\Local\Docker' | ForEach-Object { Write-Host (" - " + $_.Name) }
