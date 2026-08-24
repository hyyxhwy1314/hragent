$ErrorActionPreference = 'Stop'

# 1. Download the Cursor localization tool
$toolUrl = "https://down.vibepm.net/tools/cursor-zh.zip"
$downloadPath = "C:\Users\Administrator\Desktop\hragent\cursor-zh.zip"
$extractPath = "C:\Users\Administrator\Desktop\hragent\cursor-zh-tool"

Write-Output "Downloading Cursor localization tool..."
[Net.ServicePointManager]::SecurityProtocol = [Net.SecurityProtocolType]::Tls12
Invoke-WebRequest -Uri $toolUrl -OutFile $downloadPath -UseBasicParsing

$fileInfo = Get-Item $downloadPath
Write-Output "Downloaded: $($fileInfo.Length) bytes"

# 2. Extract
if (Test-Path $extractPath) {
    Remove-Item $extractPath -Recurse -Force
}
New-Item -ItemType Directory -Path $extractPath -Force | Out-Null

Write-Output "Extracting to: $extractPath"
Add-Type -AssemblyName System.IO.Compression.FileSystem
[System.IO.Compression.ZipFile]::ExtractToDirectory($downloadPath, $extractPath)

# 3. List contents
Write-Output "`n=== Tool contents ==="
Get-ChildItem $extractPath -Recurse | Select-Object FullName, Length | Format-Table -AutoSize

# 4. Look for the startup script
$batFiles = Get-ChildItem $extractPath -Filter "*.bat" -Recurse
$shFiles = Get-ChildItem $extractPath -Filter "*.sh" -Recurse
$jsFiles = Get-ChildItem $extractPath -Filter "*.js" -Recurse
$jsonFiles = Get-ChildItem $extractPath -Filter "*.json" -Recurse

Write-Output "`n=== BAT files ==="
$batFiles | ForEach-Object { Write-Output $_.Name }
Write-Output "`n=== JS files ==="
$jsFiles | ForEach-Object { Write-Output $_.Name }
Write-Output "`n=== JSON files ==="
$jsonFiles | ForEach-Object { Write-Output $_.Name }

# 5. Show the content of the bat file to understand what it does
if ($batFiles) {
    Write-Output "`n=== BAT file content ==="
    Get-Content $batFiles[0].FullName -Raw
}
