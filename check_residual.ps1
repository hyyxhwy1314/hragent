$paths = @(
    'C:\Users\Administrator\AppData\Local\Docker\wsl_bakROBO',
    'C:\Users\Administrator\AppData\Local\Docker\wsl_C_BACKUP_DELETE_LATER',
    'C:\Users\Administrator\AppData\Local\Docker\wsl_C_NEW_EMPTY_DELETEME'
)

foreach ($p in $paths) {
    Write-Host ("=== " + $p + " ===")
    if (Test-Path $p) {
        $files = Get-ChildItem $p -Recurse -File -ErrorAction SilentlyContinue
        $size = ($files | Measure-Object -Property Length -Sum).Sum
        Write-Host ("Size: " + [math]::Round($size/1GB,2) + " GB, File count: " + $files.Count)
        $files | ForEach-Object { Write-Host ("  " + $_.FullName + " : " + [math]::Round($_.Length/1MB,2) + " MB") }
    } else {
        Write-Host "NOT EXISTS"
    }
    Write-Host ""
}
