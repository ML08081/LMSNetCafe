param(
  [string]$DevToolsRoot = 'E:\DevTools',
  [string]$HostIp = '',
  [string]$DbHost = '', [string]$DbPort = '', [string]$DbName = '',
  [string]$DbUser = '', [string]$DbPassword = '',
  [string]$EnvFile = '.env'
)
$ErrorActionPreference = 'Stop'
$repo = Split-Path -Parent $PSScriptRoot
$logRoot = Join-Path $repo '.dev-logs'
New-Item -ItemType Directory -Force -Path $logRoot | Out-Null
foreach ($entry in @{DbHost='DB_HOST';DbPort='DB_PORT';DbName='DB_NAME';DbUser='DB_USER';DbPassword='DB_PASSWORD'}.GetEnumerator()) {
  if ($PSBoundParameters.ContainsKey($entry.Key)) { [Environment]::SetEnvironmentVariable($entry.Value, (Get-Variable $entry.Key -ValueOnly), 'Process') }
}
if (-not $HostIp) {
  $HostIp = Get-NetIPAddress -AddressFamily IPv4 -ErrorAction SilentlyContinue |
    Where-Object { $_.IPAddress -notlike '127.*' -and $_.IPAddress -notlike '169.254.*' -and $_.InterfaceAlias -notlike '*vEthernet*' } |
    Select-Object -First 1 -ExpandProperty IPAddress
  if (-not $HostIp) { $HostIp = '127.0.0.1' }
}
$registry = Join-Path $logRoot 'processes.json'
$owned = @()
if (Test-Path -LiteralPath $registry) { $owned = @(Get-Content -LiteralPath $registry -Raw | ConvertFrom-Json) }
function Start-ServiceProcess([string]$Name, [int]$Port, [string[]]$Options) {
  if (Get-NetTCPConnection -LocalPort $Port -State Listen -ErrorAction SilentlyContinue) {
    Write-Host "$Name already listening on $Port; readiness will be checked."
    return
  }
  $launcher = Join-Path $PSScriptRoot 'dev-start.ps1'
  $arguments = @('-NoProfile','-ExecutionPolicy','Bypass','-File',('"' + $launcher + '"'),'-DevToolsRoot',('"' + $DevToolsRoot + '"'),'-EnvFile',('"' + $EnvFile + '"')) + $Options
  $process = Start-Process powershell.exe -ArgumentList $arguments -WorkingDirectory $repo -WindowStyle Hidden -PassThru -RedirectStandardOutput (Join-Path $logRoot "$Name.out.log") -RedirectStandardError (Join-Path $logRoot "$Name.err.log")
  $script:owned += [pscustomobject]@{Name=$Name;Id=$process.Id;Started=$process.StartTime.ToUniversalTime().ToString('o')}
  ConvertTo-Json -InputObject @($script:owned) | Set-Content -LiteralPath $registry -Encoding UTF8
  Write-Host "Starting $Name on $Port"
}
Start-ServiceProcess 'face' 9000 @('-Face','-FaceBindHost','127.0.0.1')
Start-ServiceProcess 'backend' 8080 @('-Backend','-BindHost','0.0.0.0')
Start-ServiceProcess 'frontend' 5173 @('-Admin')
Write-Host 'Waiting for the application and database to become ready...'
$ready = $false
$deadline = (Get-Date).AddSeconds(45)
do {
  try {
    $health = (Invoke-RestMethod 'http://127.0.0.1:8080/api/v1/health' -TimeoutSec 2).data
    $page = Invoke-WebRequest 'http://127.0.0.1:5173/login' -UseBasicParsing -TimeoutSec 2
    $ready = $health.status -eq 'UP' -and $page.StatusCode -eq 200
  } catch { $ready = $false }
  if (-not $ready) { Start-Sleep -Seconds 2 }
} while (-not $ready -and (Get-Date) -lt $deadline)
Write-Host 'Local camera login: http://localhost:5173/login'
Write-Host "LAN login: http://$HostIp`:5173/login (camera access requires localhost or HTTPS)"
Write-Host "Logs: $logRoot"
if (-not $ready) { throw 'Services are not ready. Check MySQL, .env, face models and logs, then run scripts/check-demo.ps1.' }
Write-Host 'All application readiness checks passed.'
