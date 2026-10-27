param([switch]$Stop, [switch]$NoPet, [string]$RepoRoot = '')
$ErrorActionPreference = 'Stop'
if (-not $RepoRoot) { $RepoRoot = Split-Path -Parent $PSScriptRoot }
if ($Stop) {
  $registry = Join-Path $RepoRoot '.dev-logs\processes.json'
  if (-not (Test-Path -LiteralPath $registry)) { Write-Host 'No recorded project processes.'; exit 0 }
  $owned = @(Get-Content -LiteralPath $registry -Raw | ConvertFrom-Json)
  $processes = @(Get-CimInstance Win32_Process)
  function Stop-OwnedTree([int]$ProcessId) {
    foreach ($child in $processes | Where-Object ParentProcessId -eq $ProcessId) { Stop-OwnedTree $child.ProcessId }
    Stop-Process -Id $ProcessId -Force -ErrorAction SilentlyContinue
  }
  foreach ($entry in $owned) {
    $running = Get-Process -Id $entry.Id -ErrorAction SilentlyContinue
    if ($running -and $running.StartTime.ToUniversalTime().ToString('o') -eq $entry.Started) { Stop-OwnedTree $running.Id }
  }
  Remove-Item -LiteralPath $registry -Force
  Write-Host 'Stopped recorded project process trees. Shared database services remain available.'
  exit 0
}
& (Join-Path $PSScriptRoot 'start-lan-host.ps1')
if (-not $NoPet) { Write-Host 'Optional desktop client: npm run dev:pet' }
