param([switch]$SkipNode)
$ErrorActionPreference = 'Stop'
$repo = Split-Path -Parent $PSScriptRoot
Push-Location $repo
try {
  if (-not $SkipNode) {
    & npm.cmd ci
    if ($LASTEXITCODE -ne 0) { throw 'Node dependency installation failed.' }
  }
  $python = Join-Path $repo 'face-service\.venv\Scripts\python.exe'
  if (-not (Test-Path -LiteralPath $python)) {
    if (Get-Command py -ErrorAction SilentlyContinue) {
      & py -3.11 -m venv (Join-Path $repo 'face-service\.venv')
    } else {
      & python -m venv (Join-Path $repo 'face-service\.venv')
    }
    if ($LASTEXITCODE -ne 0) { throw 'Install Python 3.11 and retry.' }
  }
  & $python -m pip install -r face-service/requirements.txt
  if ($LASTEXITCODE -ne 0) { throw 'Python dependency installation failed.' }
  & $python face-service/download_models.py
  if ($LASTEXITCODE -ne 0) { throw 'Face models unavailable; check network and retry.' }
  Write-Host 'Dependencies and face models ready. Database and biometric profiles remain local.'
} finally { Pop-Location }
