# LMSNetCafe

网咖综合管理系统，按照 `开发文档` 中的架构拆分为后端主服务、管理端、人脸认证服务和桌面桌宠客户端。

## Modules

| Module | Stack | Description |
| --- | --- | --- |
| `backend` | Java 17, Spring Boot 3, Maven | REST API, auth, member, billing, device, statistics, websocket integration |
| `frontend-admin` | Vue 3, Vite, TypeScript, Element Plus | Admin console and cashier workbench |
| `face-service` | Python 3.11, FastAPI | Face enrollment, verification and identification service |
| `desktop-pet` | Electron, Vue 3, Vite, TypeScript | Client-side desktop pet and session reminder app |
| `scripts` | SQL, PowerShell | Database bootstrap and local development helpers |

## Quick Start

1. Copy `.env.example` to `.env` and adjust local database settings.
2. Create the MySQL database with `scripts/init-db.sql`.
3. Start services in this order: MySQL, Redis, `backend`, `face-service`, `frontend-admin`, `desktop-pet`.

## Local Commands

```powershell
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
python -m uvicorn app.main:app --host 127.0.0.1 --port 9000 --reload

# desktop pet
cd desktop-pet
npm install
npm run dev
```

## Notes

统一入口：`http://127.0.0.1:5173/login`。密码登录和人脸识别登录共用三角色权限体系；桌宠默认绑定演示机位 `PC-A01`。
