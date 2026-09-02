param(
  [string]$DevToolsRoot = 'E:\DevTools',
  [string]$HostIp = '',
  [string]$DbHost = '127.0.0.1',
  [string]$DbPort = '3306',
  [string]$DbName = 'lms_netcafe',
  [string]$DbUser = 'root',
  [string]$DbPassword = '123456'
)

$repoRoot = Split-Path -Parent $PSScriptRoot
$logRoot = Join-Path $repoRoot '.dev-logs'
$javaHome = Join-Path $DevToolsRoot 'Java\MicrosoftOpenJDK21'
$mavenBin = Join-Path $DevToolsRoot 'Maven\apache-maven-3.9.10\bin'
$nodeBin = Join-Path $DevToolsRoot 'Node\node-v24.19.0-win-x64'
$toolPath = (@($mavenBin, (Join-Path $javaHome 'bin'), $nodeBin) | Where-Object { Test-Path $_ }) -join ';'

New-Item -ItemType Directory -Force -Path $logRoot | Out-Null

# Pick a LAN IPv4 address and skip loopback/APIPA/WSL ranges.
function Get-LanIp {
  $ipconfigText = ipconfig | Out-String
  $matches = [regex]::Matches($ipconfigText, 'IPv4.*?:\s*([0-9]{1,3}(?:\.[0-9]{1,3}){3})')
  foreach ($match in $matches) {
    $candidate = $match.Groups[1].Value
    if (($candidate -notlike '127.*') -and ($candidate -notlike '169.254.*') -and ($candidate -notlike '172.26.*')) {
      return $candidate
    }
  }
  return '127.0.0.1'
}

# Reuse a running service on the same port.
function Test-PortListening {
  param([int]$Port)

  $client = [System.Net.Sockets.TcpClient]::new()
  try {
    $task = $client.ConnectAsync('127.0.0.1', $Port)
    return ($task.Wait(300) -and $client.Connected)
  } catch {
    return $false
  } finally {
    $client.Dispose()
  }
}

# Start services in the background and write logs to .dev-logs.
function Start-HiddenService {
  param(
    [string]$Name,
    [string]$Command,
    [int]$Port
  )

  if (Test-PortListening $Port) {
    Write-Host "$Name already listening on port $Port"
    return
  }
  Start-Process powershell -WindowStyle Hidden -ArgumentList '-NoProfile','-ExecutionPolicy','Bypass','-Command',$Command
  Write-Host "Starting $Name on port $Port"
}

if (-not $HostIp) {
  $HostIp = Get-LanIp
}

$pathValue = if ($toolPath) { "$toolPath;$env:Path" } else { $env:Path }
$faceCommand = "`$env:Path='$pathValue'; Set-Location '$repoRoot'; .\scripts\dev-start.ps1 -Face -FaceBindHost 127.0.0.1 *> .dev-logs\face.log"
$backendCommand = "`$env:JAVA_HOME='$javaHome'; `$env:Path='$pathValue'; `$env:DB_HOST='$DbHost'; `$env:DB_PORT='$DbPort'; `$env:DB_NAME='$DbName'; `$env:DB_USER='$DbUser'; `$env:DB_PASSWORD='$DbPassword'; `$env:FACE_SERVICE_URL='http://127.0.0.1:9000'; `$env:CORS_ALLOWED_ORIGIN_PATTERNS='http://localhost:*,http://127.0.0.1:*,http://192.168.*:*,http://172.*:*,http://10.*:*'; Set-Location '$repoRoot'; .\scripts\dev-start.ps1 -Backend -BindHost 0.0.0.0 *> .dev-logs\backend.log"
$adminCommand = "`$env:Path='$pathValue'; Set-Location '$repoRoot'; .\scripts\dev-start.ps1 -Admin -BackendBaseUrl http://127.0.0.1:8080 *> .dev-logs\frontend.log"

Start-HiddenService 'face-service' $faceCommand 9000
Start-Sleep -Seconds 2
Start-HiddenService 'backend' $backendCommand 8080
Start-Sleep -Seconds 5
Start-HiddenService 'frontend-admin' $adminCommand 5173
Start-Sleep -Seconds 3

Write-Host ''
Write-Host 'LMSNetCafe LAN services are starting.'
Write-Host "Login URL: http://$HostIp`:5173/login"
Write-Host "Backend health: http://$HostIp`:8080/api/v1/health"
Write-Host "Logs: $logRoot"
