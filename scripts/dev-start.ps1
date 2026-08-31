param(
  [switch]$Admin,
  [switch]$Face,
  [switch]$Pet
)

$repoRoot = Split-Path -Parent $PSScriptRoot

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
