<#
.SYNOPSIS
  LMSNetCafe 本机一键启动/停止脚本（适配当前电脑环境）

.DESCRIPTION
  自动检测并启动 Redis、MySQL、后端(8080)、人脸服务(9000)、管理前端(5173)。
  所有服务在后台运行，日志写入 .dev-logs 目录。

.PARAMETER Stop
  停止所有由本脚本启动的服务。

.PARAMETER NoPet
  不提示桌宠启动方式（默认会提示）。

.PARAMETER RepoRoot
  项目根目录，默认自动检测。

.EXAMPLE
  .\scripts\start-local.ps1
  .\scripts\start-local.ps1 -Stop
#>

param(
  [switch]$Stop,
  [switch]$NoPet,
  [string]$RepoRoot = ""
)

# ---------- 路径与常量 ----------
if (-not $RepoRoot) {
  $RepoRoot = Split-Path -Parent $PSScriptRoot
}
$LogDir = Join-Path $RepoRoot ".dev-logs"
$RedisExe = "D:\DevTools\Redis\redis-server.exe"
$FacePython = Join-Path $RepoRoot "face-service\.venv\Scripts\python.exe"
$BackendDir = Join-Path $RepoRoot "backend"
$FrontendDir = Join-Path $RepoRoot "frontend-admin"
$PetDir = Join-Path $RepoRoot "desktop-pet"

if (-not (Test-Path $LogDir)) {
  New-Item -ItemType Directory -Path $LogDir -Force | Out-Null
}

# ---------- 工具函数 ----------
function Test-PortListening {
  param([int]$Port)
  $conn = Get-NetTCPConnection -LocalPort $Port -State Listen -ErrorAction SilentlyContinue
  return [bool]$conn
}

function Write-Step {
  param([string]$Msg)
  Write-Host "[$(Get-Date -Format 'HH:mm:ss')] $Msg" -ForegroundColor Cyan
}

function Write-Ok {
  param([string]$Msg)
  Write-Host "  OK  $Msg" -ForegroundColor Green
}

function Write-Warn {
  param([string]$Msg)
  Write-Host "  WARN $Msg" -ForegroundColor Yellow
}

# ---------- 停止模式 ----------
if ($Stop) {
  Write-Step "Stopping all LMSNetCafe services..."

  # 后端 java 进程（通过命令行参数匹配）
  Get-CimInstance Win32_Process -Filter "Name='java.exe'" -ErrorAction SilentlyContinue |
    Where-Object { $_.CommandLine -like "*spring-boot*" -or $_.CommandLine -like "*netcafe*" } |
    ForEach-Object {
      Stop-Process -Id $_.ProcessId -Force -ErrorAction SilentlyContinue
      Write-Ok "Stopped backend java (PID $($_.ProcessId))"
    }

  # node 进程（vite / electron）
  Get-CimInstance Win32_Process -Filter "Name='node.exe'" -ErrorAction SilentlyContinue |
    Where-Object { $_.CommandLine -like "*vite*" -or $_.CommandLine -like "*electron*" } |
    ForEach-Object {
      Stop-Process -Id $_.ProcessId -Force -ErrorAction SilentlyContinue
      Write-Ok "Stopped node (PID $($_.ProcessId))"
    }

  # face-service python
  Get-CimInstance Win32_Process -Filter "Name='python.exe'" -ErrorAction SilentlyContinue |
    Where-Object { $_.CommandLine -like "*uvicorn*" } |
    ForEach-Object {
      Stop-Process -Id $_.ProcessId -Force -ErrorAction SilentlyContinue
      Write-Ok "Stopped face-service python (PID $($_.ProcessId))"
    }

  # redis
  Get-Process -Name "redis-server" -ErrorAction SilentlyContinue |
    ForEach-Object {
      Stop-Process -Id $_.Id -Force -ErrorAction SilentlyContinue
      Write-Ok "Stopped redis-server (PID $($_.Id))"
    }

  Write-Step "All services stopped."
  exit 0
}

# ---------- 启动模式 ----------
Write-Host ""
Write-Host "=== LMSNetCafe 本机启动 ===" -ForegroundColor White
Write-Host "Repo: $RepoRoot"
Write-Host ""

# 1. Redis
if (Test-PortListening 6379) {
  Write-Ok "Redis already running on 6379"
} elseif (Test-Path $RedisExe) {
  Start-Process -FilePath $RedisExe -WindowStyle Hidden
  Start-Sleep -Seconds 2
  if (Test-PortListening 6379) {
    Write-Ok "Redis started on 6379"
  } else {
    Write-Warn "Redis may have failed to start"
  }
} else {
  Write-Warn "Redis executable not found at $RedisExe"
}

# 2. MySQL
$mysqlSvc = Get-Service -Name "MySQL80" -ErrorAction SilentlyContinue
if ($mysqlSvc) {
  if ($mysqlSvc.Status -eq "Running") {
    Write-Ok "MySQL80 service already running"
  } else {
    try {
      Start-Service -Name "MySQL80" -ErrorAction Stop
      Start-Sleep -Seconds 2
      Write-Ok "MySQL80 service started"
    } catch {
      Write-Warn "Failed to start MySQL80: $($_.Exception.Message)"
    }
  }
} else {
  Write-Warn "MySQL80 service not found (check MySQL installation)"
}

# 3. face-service
if (Test-PortListening 9000) {
  Write-Ok "face-service already running on 9000"
} elseif (Test-Path $FacePython) {
  $outLog = Join-Path $LogDir "face.out.log"
  $errLog = Join-Path $LogDir "face.err.log"
  Start-Process -FilePath $FacePython `
    -ArgumentList "-m","uvicorn","app.main:app","--host","127.0.0.1","--port","9000" `
    -WorkingDirectory (Join-Path $RepoRoot "face-service") `
    -WindowStyle Hidden `
    -RedirectStandardOutput $outLog `
    -RedirectStandardError $errLog
  Write-Step "face-service starting on 9000..."
} else {
  Write-Warn "face-service venv python not found at $FacePython"
}

# 4. 后端（等待 face-service 就绪后启动）
Start-Sleep -Seconds 3
if (Test-PortListening 8080) {
  Write-Ok "backend already running on 8080"
} else {
  $outLog = Join-Path $LogDir "backend.out.log"
  $errLog = Join-Path $LogDir "backend.err.log"
  $backendCmd = "Set-Location '$BackendDir'; mvn spring-boot:run *> '$outLog' 2> '$errLog'"
  Start-Process -FilePath "powershell.exe" `
    -ArgumentList "-NoProfile","-ExecutionPolicy","Bypass","-Command",$backendCmd `
    -WindowStyle Hidden
  Write-Step "backend starting on 8080 (may take 20-40s)..."
}

# 5. 前端管理台
Start-Sleep -Seconds 2
if (Test-PortListening 5173) {
  Write-Ok "frontend-admin already running on 5173"
} else {
  $outLog = Join-Path $LogDir "frontend.out.log"
  $errLog = Join-Path $LogDir "frontend.err.log"
  $adminCmd = "Set-Location '$FrontendDir'; npm run dev *> '$outLog' 2> '$errLog'"
  Start-Process -FilePath "powershell.exe" `
    -ArgumentList "-NoProfile","-ExecutionPolicy","Bypass","-Command",$adminCmd `
    -WindowStyle Hidden
  Write-Step "frontend-admin starting on 5173..."
}

# ---------- 等待并验证 ----------
Write-Host ""
Write-Step "Waiting for services to come up..."
$allOk = $true
$checks = @(
  @{Port=6379; Name="Redis"},
  @{Port=9000; Name="face-service"},
  @{Port=8080; Name="backend"},
  @{Port=5173; Name="frontend-admin"}
)

for ($i = 0; $i -lt 30; $i++) {
  Start-Sleep -Seconds 2
  $ready = $true
  foreach ($c in $checks) {
    if (-not (Test-PortListening $c.Port)) {
      $ready = $false
      break
    }
  }
  if ($ready) { break }
}

Write-Host ""
foreach ($c in $checks) {
  if (Test-PortListening $c.Port) {
    Write-Ok "$($c.Name) on port $($c.Port)"
  } else {
    Write-Warn "$($c.Name) NOT responding on port $($c.Port)"
    $allOk = $false
  }
}

# ---------- 结果输出 ----------
Write-Host ""
if ($allOk) {
  Write-Host "=== 所有服务启动成功 ===" -ForegroundColor Green
} else {
  Write-Host "=== 部分服务未就绪，请检查日志 ===" -ForegroundColor Yellow
}
Write-Host "登录地址: http://127.0.0.1:5173/login"
Write-Host "后端文档: http://127.0.0.1:8080/swagger-ui.html"
Write-Host "人脸文档: http://127.0.0.1:9000/docs"
Write-Host "日志目录: $LogDir"

if (-not $NoPet) {
  Write-Host ""
  Write-Host "桌宠客户端（需手动启动，会弹出窗口）：" -ForegroundColor White
  Write-Host "  cd $PetDir"
  Write-Host "  npm run dev"
}
Write-Host ""
