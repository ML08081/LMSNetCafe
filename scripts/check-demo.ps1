param([string]$BackendUrl = 'http://127.0.0.1:8080', [string]$FrontendUrl = 'http://127.0.0.1:5173')
$ErrorActionPreference = 'Stop'
$failed = $false
try {
  $health = (Invoke-RestMethod "$BackendUrl/api/v1/health" -TimeoutSec 20).data
  Write-Host "Backend: $($health.status); Database: $($health.databaseReady); Face models: $($health.face.ready)"
  if ($health.status -ne 'UP') { $failed = $true; Write-Warning $health.face.message }
  if ($health.face.profilesRequiringEnrollment -gt 0) {
    Write-Warning 'Legacy biometric profiles detected. Re-enroll these users before the face demonstration.'
  }
} catch { $failed = $true; Write-Warning 'Backend is unavailable. Check .dev-logs and database configuration.' }
try {
  $response = Invoke-WebRequest "$FrontendUrl/login" -UseBasicParsing -TimeoutSec 5
  if ($response.StatusCode -ne 200) { throw 'Frontend unavailable' }
  Write-Host 'Frontend: ready'
} catch { $failed = $true; Write-Warning 'Frontend is unavailable.' }
Write-Host 'Camera: use http://localhost:5173 on the camera computer, or an HTTPS site.'
Write-Host 'Before presenting, enroll your own face and complete a fresh member start/recharge/settle rehearsal.'
if ($failed) { exit 1 }
Write-Host 'Service checks passed. Physical camera and live face acceptance still require the rehearsal.'
