param([Parameter(Mandatory=$true)][ValidatePattern('^[A-Za-z0-9._-]+$')][string]$Distro)
$ErrorActionPreference = 'Stop'
$repo = Split-Path -Parent $PSScriptRoot
$logs = Join-Path $repo '.dev-logs'
New-Item -ItemType Directory -Force -Path $logs | Out-Null
$registry = Join-Path $logs 'processes.json'
$owned = @()
if (Test-Path -LiteralPath $registry) {
  foreach ($entry in (Get-Content -LiteralPath $registry -Raw | ConvertFrom-Json)) {
    if ($entry.Name -and $entry.Id) { $owned += [pscustomobject]@{Name=[string]$entry.Name;Id=[int]$entry.Id;Started=[string]$entry.Started} }
  }
}
foreach ($entry in $owned | Where-Object Name -eq 'wsl-database') {
  $process = Get-Process -Id $entry.Id -ErrorAction SilentlyContinue
  if ($process -and $process.StartTime.ToUniversalTime() -eq ([datetime]$entry.Started).ToUniversalTime()) {
    Write-Host "WSL database session already active: $Distro"
    return
  }
}
# A live WSL command keeps the distro running; systemd services alone may not.
# Start the existing MySQL service only. Never initialize, import or reset a database here.
$arguments = @('-d', $Distro, '-u', 'root', '--exec', 'sh', '-c', '"service mysql start && exec sleep infinity"')
$process = Start-Process wsl.exe -ArgumentList $arguments -WindowStyle Hidden -PassThru `
  -RedirectStandardOutput (Join-Path $logs 'wsl-database.out.log') `
  -RedirectStandardError (Join-Path $logs 'wsl-database.err.log')
$owned = @($owned | Where-Object Name -ne 'wsl-database')
$owned += [pscustomobject]@{Name='wsl-database';Id=$process.Id;Started=$process.StartTime.ToUniversalTime().ToString('o')}
$owned | ConvertTo-Json -Depth 4 | Set-Content -LiteralPath $registry -Encoding UTF8
Start-Sleep -Seconds 2
if ($process.HasExited) { throw "WSL MySQL could not start. Check .dev-logs/wsl-database.err.log and distro $Distro." }
Write-Host "WSL MySQL session started: $Distro"
