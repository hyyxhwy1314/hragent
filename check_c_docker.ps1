Write-Host "=== C:\Users\Administrator\AppData\Local\Docker full tree ==="
$root = 'C:\Users\Administrator\AppData\Local\Docker'
Get-ChildItem $root -Recurse -Force -ErrorAction SilentlyContinue | ForEach-Object {
    $rel = $_.FullName.Substring($root.Length)
    $type = if ($_.PSIsContainer) { "[DIR]" } else { "[FILE " + [math]::Round($_.Length/1KB,2) + " KB]" }
    Write-Host ($type + " " + $rel)
}

Write-Host ""
Write-Host "=== Total size ==="
$total = (Get-ChildItem $root -Recurse -File -ErrorAction SilentlyContinue | Measure-Object -Property Length -Sum).Sum
Write-Host ("Total: " + [math]::Round($total/1GB,2) + " GB")
