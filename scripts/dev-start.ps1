param(
  [switch]$Backend,
  [switch]$Admin,
  [switch]$Face,
  [switch]$Pet,
  [string]$DevToolsRoot = 'E:\DevTools'
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

if ($Backend) {
  Push-Location (Join-Path $repoRoot 'backend')
  mvn spring-boot:run
  Pop-Location
}

if ($Admin) {
  Push-Location (Join-Path $repoRoot 'frontend-admin')
  npm run dev
  Pop-Location
}

if ($Face) {
  Push-Location (Join-Path $repoRoot 'face-service')
  uvicorn app.main:app --host 0.0.0.0 --port 9000 --reload
  Pop-Location
}

if ($Pet) {
  Push-Location (Join-Path $repoRoot 'desktop-pet')
  npm run dev
  Pop-Location
}
