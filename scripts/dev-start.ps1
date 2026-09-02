param(
  [switch]$Backend,
  [switch]$Admin,
  [switch]$Face,
  [switch]$Pet,
  [string]$DevToolsRoot = 'E:\DevTools',
  [string]$EnvFile = '',
  [string]$BindHost = '0.0.0.0',
  [string]$FaceBindHost = '127.0.0.1',
  [string]$BackendBaseUrl = 'http://127.0.0.1:8080',
  [string]$PetServerUrl = '',
  [string]$PetDeviceCode = ''
)

$repoRoot = Split-Path -Parent $PSScriptRoot
$javaHome = Join-Path $DevToolsRoot 'Java\MicrosoftOpenJDK21'
$mavenBin = Join-Path $DevToolsRoot 'Maven\apache-maven-3.9.10\bin'
$nodeBin = Join-Path $DevToolsRoot 'Node\node-v24.19.0-win-x64'

if (Test-Path $javaHome) {
  $env:JAVA_HOME = $javaHome
}

$toolPaths = @($mavenBin, (Join-Path $javaHome 'bin'), $nodeBin) | Where-Object { Test-Path $_ }
$env:Path = ($toolPaths -join ';') + ';' + $env:Path

# Load .env values into the current PowerShell process only.
function Import-EnvFile {
  param([string]$Path)

  if (-not $Path) { return }
  $resolved = if ([System.IO.Path]::IsPathRooted($Path)) { $Path } else { Join-Path $repoRoot $Path }
  if (-not (Test-Path $resolved)) {
    Write-Warning "Env file not found: $resolved"
    return
  }
  Get-Content $resolved | ForEach-Object {
    $line = $_.Trim()
    if (-not $line -or $line.StartsWith('#') -or -not $line.Contains('=')) { return }
    $name, $value = $line.Split('=', 2)
    [Environment]::SetEnvironmentVariable($name.Trim(), $value.Trim(), 'Process')
  }
}

Import-EnvFile $EnvFile

# Vite uses this backend URL for API proxying.
$env:VITE_BACKEND_BASE_URL = $BackendBaseUrl
$env:VITE_PROXY_API_TARGET = $BackendBaseUrl
if ($PetServerUrl) { $env:LMS_PET_SERVER_URL = $PetServerUrl }
if ($PetDeviceCode) { $env:LMS_PET_DEVICE_CODE = $PetDeviceCode }

if ($Backend) {
  Push-Location (Join-Path $repoRoot 'backend')
  mvn spring-boot:run "-Dspring-boot.run.arguments=--server.address=$BindHost"
  Pop-Location
}

if ($Admin) {
  Push-Location (Join-Path $repoRoot 'frontend-admin')
  npm run dev
  Pop-Location
}

if ($Face) {
  Push-Location (Join-Path $repoRoot 'face-service')
  python -m uvicorn app.main:app --host $FaceBindHost --port 9000 --reload
  Pop-Location
}

if ($Pet) {
  Push-Location (Join-Path $repoRoot 'desktop-pet')
  npm run dev
  Pop-Location
}
