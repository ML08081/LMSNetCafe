param([switch]$Install)
$ErrorActionPreference = 'Stop'
$repo = Split-Path -Parent $PSScriptRoot
Push-Location $repo
try {
  & git diff --quiet
  if ($LASTEXITCODE -ne 0) { throw 'Commit or stash local tracked changes before updating.' }
  & git diff --cached --quiet
  if ($LASTEXITCODE -ne 0) { throw 'Commit staged changes before updating.' }
  $branch = (& git branch --show-current).Trim()
  if (-not $branch) { throw 'Switch to a branch before updating.' }
  & git fetch origin $branch
  if ($LASTEXITCODE -ne 0) { throw 'GitHub fetch failed; check your connection and Git credentials.' }
  & git merge --ff-only "origin/$branch"
  if ($LASTEXITCODE -ne 0) { throw 'Branches diverged; resolve the commits before updating. No local work was discarded.' }
  & git log -1 --format='%h %s'
  if ($Install) { & (Join-Path $PSScriptRoot 'setup-project.ps1') }
  Write-Host 'Source is synchronized. Restart running services to load the new version.'
} finally { Pop-Location }
