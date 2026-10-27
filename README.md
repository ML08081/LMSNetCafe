# LMSNetCafe

网咖综合管理系统，按照 `开发文档` 中的架构拆分为后端主服务、管理端、人脸认证服务和桌面桌宠客户端。

新增智能服务助手与 AI 经营日报：会员可查询本人余额/计费并获取操作指引，管理员可按日期生成、保存、查看及导出经营日报。已完成通用模型接入，默认使用明确标注的基础问答与统计摘要，后续在主机 `.env` 配置兼容模型后启用 AI。见 [AI 功能配置与验收](开发文档/21_智能服务助手与AI经营日报.md)。

2026-10-27 整改版：新增会员自助上机/下机、真实人脸核验上机、计费预览和价格快照、低余额提醒、异常会话检查、会话筛选和 CSV 导出。具体问题、业务规则、升级和现场演示步骤见 [导师问题整改与演示验收](开发文档/20_导师问题整改与演示验收.md)。

另一台电脑首次获取这次整改时，先在项目目录运行 `git pull --ff-only`，再运行 `npm run setup`。以后可用 `npm run sync` 拉取代码并安装依赖、校验下载模型，然后重启服务，运行 `npm run check:demo`。Git 不同步数据库、`.env` 或个人的人脸特征；需要共享这些数据时，两台前端连接同一个后端与人脸服务。

## Modules

| Module | Stack | Description |
| --- | --- | --- |
| `backend` | Java 17, Spring Boot 3, Maven | REST API, auth, member, billing, device, statistics, websocket integration |
| `frontend-admin` | Vue 3, Vite, TypeScript, Element Plus | Admin console and cashier workbench |
| `face-service` | Python 3.11, FastAPI | Face enrollment, verification and identification service |
| `desktop-pet` | Electron, Vue 3, Vite, TypeScript | Client-side desktop pet and session reminder app |
| `scripts` | SQL, PowerShell | Database bootstrap and local development helpers |
| `deploy` | env examples | LAN host/client environment templates |
| `开发文档` | Markdown | Requirement, architecture, database, thesis, LAN deployment and migration guides |
| `.vscode` | VSCode settings | Recommended workspace settings, extensions and tasks |

## Quick Start

1. Copy `.env.example` to `.env` and adjust local database settings.
2. **仅新建演示数据库**时执行 `scripts/init-db.sql`。已有数据库升级不要重跑该脚本，它会写入并更新演示数据。
3. Run `npm run setup` to install dependencies and download verified face models.
4. Start MySQL, then run `npm run start:lan`. The optional desktop client can be started with `npm run dev:pet`.
5. Re-enroll your own face after upgrading, then run `npm run check:demo` and rehearse enrollment, start and settlement.

## Local Commands

```powershell
# one-click LAN host startup
npm run start:lan

# backend
cd backend
mvn spring-boot:run

# admin web
cd frontend-admin
npm install
npm run dev

# face service
cd face-service
python -m venv .venv
.\.venv\Scripts\Activate.ps1
pip install -r requirements.txt
python download_models.py
python -m uvicorn app.main:app --host 127.0.0.1 --port 9000 --reload

# desktop pet
cd desktop-pet
npm install
npm run dev
```

## Notes

统一入口：`http://127.0.0.1:5173/login`。局域网访问时使用主机脚本输出的 `http://主机IP:5173/login`。密码登录和人脸识别登录共用三角色权限体系；桌宠默认绑定演示机位 `PC-A01`。

摄像头必须在本机 `http://localhost:5173` 或 HTTPS 下使用。另一台电脑可以运行本地前端代理，将 `BackendBaseUrl` 指向主机后端，再在本机 localhost 打开页面。人脸照片选择入口不代表活体检测。上机表示创建计费会话，桌宠轮询展示状态，不包含硬件开机或 Windows 锁屏控制。

数据库已在 WSL 中时，在本机 `.env` 设置 `WSL_DISTRO=Ubuntu`（改成实际发行版名称）。`npm run start:lan` 会启动该发行版已有的 MySQL，并保持 WSL 会话，避免空闲退出导致数据库断连；不会创建或重置数据库。其他电脑没有使用 WSL 时留空该项。

## Verification

```powershell
mvn -f backend/pom.xml test
npm run build:admin
npm run build:pet
cd face-service
.\.venv\Scripts\python.exe tests/download_fixtures.py
.\.venv\Scripts\python.exe -m unittest discover -s tests -v
```

Java 本地测试使用独立的 H2 测试库；GitHub Actions 另执行真实 MySQL 8 回归。不要把测试数据源改成业务数据库，生命周期测试会清理测试表。
