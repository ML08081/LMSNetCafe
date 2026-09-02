param(
  [string]$OutputDir = 'packages',
  [string]$PackageName = 'LMSNetCafe-lan-package'
)

$repoRoot = Split-Path -Parent $PSScriptRoot
$outputRoot = if ([System.IO.Path]::IsPathRooted($OutputDir)) { $OutputDir } else { Join-Path $repoRoot $OutputDir }
$stagingRoot = Join-Path $outputRoot $PackageName
$zipPath = Join-Path $outputRoot "$PackageName.zip"
$resolvedRepo = [System.IO.Path]::GetFullPath($repoRoot)
$resolvedOutput = [System.IO.Path]::GetFullPath($outputRoot)
$resolvedStaging = [System.IO.Path]::GetFullPath($stagingRoot)

if (-not $resolvedOutput.StartsWith($resolvedRepo, [System.StringComparison]::OrdinalIgnoreCase)) {
  throw "OutputDir must be inside the repository: $resolvedRepo"
}

$excludedDirs = @(
  '.git', '.idea', '.vscode', '.dev-logs', 'node_modules', 'target', 'dist',
  'dist-electron', '.vite', '.venv', 'venv', '__pycache__', 'packages', 'logs'
)
$excludedFiles = @(
  '*.log', '*.class', '*.pyc', '*.pyo', '*.tsbuildinfo', '*.npz',
  'vite.config.js', 'vite.config.d.ts', 'wuhuang-cat.png',
  '.env', '.env.local', '.env.development', '.env.production'
)

New-Item -ItemType Directory -Force -Path $outputRoot | Out-Null
if (Test-Path $resolvedStaging) {
  if (-not $resolvedStaging.StartsWith($resolvedOutput, [System.StringComparison]::OrdinalIgnoreCase)) {
    throw "Refusing to clean staging outside output directory: $resolvedStaging"
  }
  Remove-Item -LiteralPath $resolvedStaging -Recurse -Force
}
if (Test-Path $zipPath) {
  Remove-Item -LiteralPath $zipPath -Force
}
New-Item -ItemType Directory -Force -Path $resolvedStaging | Out-Null

$robocopyArgs = @(
  $repoRoot, $resolvedStaging, '/E',
  '/XD'
) + $excludedDirs + @(
  '/XF'
) + $excludedFiles + @(
  '/NFL', '/NDL', '/NJH', '/NJS', '/NP'
)
& robocopy @robocopyArgs | Out-Null
if ($LASTEXITCODE -gt 7) {
  throw "robocopy failed with exit code $LASTEXITCODE"
}

Compress-Archive -Path (Join-Path $resolvedStaging '*') -DestinationPath $zipPath -Force
Write-Host "LAN package created: $zipPath"
$global:LASTEXITCODE = 0
