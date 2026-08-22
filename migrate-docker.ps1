# Docker Desktop Data Migration Script
# Moves WSL data folder reference from C: to D:\DockerData
# Run after files have been copied to D:\DockerData\wsl

$ErrorActionPreference = "Stop"

Write-Host "=== Step 1: Update settings-store.json (add DataFolder) ===" -ForegroundColor Cyan
$settingsPath = "$env:APPDATA\Docker\settings-store.json"
$settings = Get-Content $settingsPath -Raw | ConvertFrom-Json
if (-not ($settings.PSObject.Properties.Name -contains "DataFolder")) {
    $settings | Add-Member -NotePropertyName "DataFolder" -NotePropertyValue "D:\DockerData" -Force
    $json = $settings | ConvertTo-Json -Depth 10
    [System.IO.File]::WriteAllText($settingsPath, $json, [System.Text.UTF8Encoding]::new($false))
    Write-Host "OK: Added DataFolder = D:\DockerData to $settingsPath" -ForegroundColor Green
} else {
    $settings.DataFolder = "D:\DockerData"
    $json = $settings | ConvertTo-Json -Depth 10
    [System.IO.File]::WriteAllText($settingsPath, $json, [System.Text.UTF8Encoding]::new($false))
    Write-Host "OK: Updated DataFolder = D:\DockerData in $settingsPath" -ForegroundColor Green
}
Write-Host "Content:"
Get-Content $settingsPath
Write-Host ""

Write-Host "=== Step 2: Verify D:\DockerData\wsl files ===" -ForegroundColor Cyan
$targetWsl = "D:\DockerData\wsl"
if (Test-Path $targetWsl) {
    $files = Get-ChildItem $targetWsl -Recurse -File
    $totalGB = 0
    foreach ($f in $files) {
        $sizeGB = [math]::Round($f.Length/1GB, 2)
        $totalGB += $sizeGB
        Write-Host "  $($f.FullName)  $sizeGB GB"
    }
    Write-Host "Total: $totalGB GB" -ForegroundColor Green
} else {
    Write-Host "ERROR: $targetWsl not found!" -ForegroundColor Red
    exit 1
}
Write-Host ""

Write-Host "=== Step 3: Rename original WSL folder as backup ===" -ForegroundColor Cyan
$oldWsl = "$env:LOCALAPPDATA\Docker\wsl"
$timestamp = Get-Date -Format "yyyyMMddHHmmss"
$bakWsl = "$env:LOCALAPPDATA\Docker\wsl_bak_$timestamp"
if (Test-Path $oldWsl) {
    Rename-Item -Path $oldWsl -NewName (Split-Path $bakWsl -Leaf) -Force
    if (Test-Path $bakWsl) {
        Write-Host "OK: Renamed $oldWsl -> $bakWsl" -ForegroundColor Green
    } else {
        Write-Host "WARNING: Rename may have failed, checking..."
        if (Test-Path $oldWsl) { Write-Host "ERROR: Original still exists" -ForegroundColor Red; exit 1 }
    }
} else {
    Write-Host "SKIP: Original WSL folder not found (already moved?)" -ForegroundColor Yellow
}
Write-Host ""

Write-Host "=== Step 4: Sanity check before starting Docker ===" -ForegroundColor Cyan
$remainingDockerProcs = Get-Process "Docker Desktop","com.docker.backend","com.docker.build","dockerd","containerd" -ErrorAction SilentlyContinue
if ($remainingDockerProcs) {
    Write-Host "WARNING: Docker processes still running, killing them..." -ForegroundColor Yellow
    $remainingDockerProcs | Stop-Process -Force -ErrorAction SilentlyContinue
    Start-Sleep -Seconds 3
}
wsl --shutdown 2>&1 | Out-Null
Write-Host "OK: WSL shut down, no stray Docker processes" -ForegroundColor Green
Write-Host ""

Write-Host "=== Migration steps complete. Ready to launch Docker Desktop. ===" -ForegroundColor Green
Write-Host "Files preserved (you can delete these later after successful verification):"
Write-Host "  - Original WSL backup: $bakWsl"
Write-Host "  - New WSL data: D:\DockerData\wsl"
Write-Host ""
Write-Host "Next: Start Docker Desktop, then run 'docker info' and 'docker images' to verify all data intact."
