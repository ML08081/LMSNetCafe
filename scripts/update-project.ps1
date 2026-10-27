param([switch]$Install, [string]$ProxyUrl = '', [switch]$Direct)
$ErrorActionPreference = 'Stop'
$repo = Split-Path -Parent $PSScriptRoot
Push-Location $repo
try {
  # Git does not automatically inherit the Windows Internet Settings proxy.
  # Apply it to this fetch only; never change the user's global Git configuration.
  if (-not $Direct -and -not $ProxyUrl -and -not $env:HTTPS_PROXY -and -not $env:ALL_PROXY) {
    $settings = Get-ItemProperty 'HKCU:\Software\Microsoft\Windows\CurrentVersion\Internet Settings' -ErrorAction SilentlyContinue
    if ($settings.ProxyEnable -eq 1 -and $settings.ProxyServer) {
      $server = $settings.ProxyServer
      if ($server -match '(?:^|;)https=([^;]+)') { $server = $Matches[1] }
      elseif ($server.Contains('=')) { $server = '' }
      if ($server) { $ProxyUrl = if ($server.Contains('://')) { $server } else { "http://$server" } }
    }
  }
  $gitOptions = @()
  if ($ProxyUrl -and -not $Direct) { $gitOptions = @('-c', "http.proxy=$ProxyUrl") }
  & git diff --quiet
  if ($LASTEXITCODE -ne 0) { throw 'Commit or stash local tracked changes before updating.' }
  & git diff --cached --quiet
  if ($LASTEXITCODE -ne 0) { throw 'Commit staged changes before updating.' }
  $branch = (& git branch --show-current).Trim()
  if (-not $branch) { throw 'Switch to a branch before updating.' }
  & git @gitOptions fetch origin $branch
  if ($LASTEXITCODE -ne 0) { throw 'GitHub fetch failed; check your connection and Git credentials.' }
  & git merge --ff-only "origin/$branch"
  if ($LASTEXITCODE -ne 0) { throw 'Branches diverged; resolve the commits before updating. No local work was discarded.' }
  & git log -1 --format='%h %s'
  if ($Install) { & (Join-Path $PSScriptRoot 'setup-project.ps1') }
  Write-Host 'Source is synchronized. Restart running services to load the new version.'
} finally { Pop-Location }
