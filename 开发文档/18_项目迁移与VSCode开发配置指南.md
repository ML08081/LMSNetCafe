# 项目迁移与 VSCode 开发配置指南

编写日期：2026-09-02

## 1. 迁移目标

本文档用于指导将 `F:\LMSNetCafe` 项目迁移到 `G:\LMSNetpj`，再复制到另一台电脑中进行开发和使用。迁移后的项目目录保持清晰结构，保留源码、脚本、文档和示例配置，同时排除依赖目录、构建产物、日志、真实 `.env` 和运行期人脸特征数据。

如果数据库仍保留在原电脑上，另一台电脑可以作为访问端或开发端使用。最稳的方式是：原电脑继续运行数据库和后端，另一台电脑通过浏览器、前端开发服务或桌宠连接原电脑的后端地址。

## 2. 项目目录结构

```text
LMSNetpj
├── backend                 Spring Boot 后端服务
├── frontend-admin          Vue 网页端，包含管理员、前台和普通用户门户
├── face-service            FastAPI 人脸识别服务
├── desktop-pet             Electron 桌面宠物客户端
├── scripts                 数据库初始化、启动、打包脚本
├── deploy                  局域网主机和访问端环境变量示例
├── docs                    仓库和开发辅助文档
├── 开发文档                需求、架构、数据库、论文和迁移部署文档
├── .vscode                 VSCode 推荐插件、设置和任务
├── .env.example            根目录环境变量示例
├── package.json            根目录统一 npm 命令入口
└── README.md               项目入口说明
```

## 3. 迁移包内容原则

保留内容：

- Java、Vue、Python、Electron 源码。
- `scripts/init-db.sql` 数据库初始化脚本。
- `deploy/*.env.example` 环境变量示例。
- `开发文档`、`docs` 和 README。
- `.vscode/settings.json`、`.vscode/tasks.json`、`.vscode/extensions.json`。

排除内容：

- `node_modules`
- `backend/target`
- `frontend-admin/dist`
- `desktop-pet/dist`
- `desktop-pet/dist-electron`
- `face-service/.venv`
- `.dev-logs`
- `packages`
- 真实 `.env`
- 人脸运行期特征文件 `*.npz`
- 旧版桌宠图片 `wuhuang-cat.png`

## 4. 在当前电脑生成和复制

当前我已经把项目复制到：

```text
G:\LMSNetpj
```

后续如需重新生成迁移包，可以在原项目目录执行：

```powershell
cd F:\LMSNetCafe
npm run package:lan
```

也可以直接复制项目目录，但要避免复制依赖、构建产物和真实环境变量文件。

## 5. 另一台电脑环境准备

### 5.1 必备软件

| 软件 | 建议版本 | 用途 |
| --- | --- | --- |
| VSCode | 最新稳定版 | 代码编辑 |
| Node.js | 20+ 或当前项目使用的 24.19.0 | 前端和桌宠 |
| JDK | 17+，推荐 21 | 后端编译运行 |
| Maven | 3.9+ | 后端依赖和启动 |
| Python | 3.11 | 人脸服务 |
| Git | 最新稳定版 | 版本管理 |

如果另一台电脑只作为浏览器访问端，则不需要安装这些开发工具。

### 5.2 VSCode 推荐插件

打开 `G:\LMSNetpj` 后，VSCode 会根据 `.vscode/extensions.json` 推荐以下插件：

- Vue - Official / Volar
- Extension Pack for Java
- Maven for Java
- Python
- Pylance
- Prettier

## 6. 另一台电脑作为访问端

这是最简单方式。原电脑启动系统后，另一台电脑只需要打开：

```text
http://原电脑IP:5173/login
```

原电脑可以用一键脚本启动：

```powershell
cd F:\LMSNetCafe
npm run start:lan
```

脚本会输出类似：

```text
Login URL: http://192.168.1.191:5173/login
Backend health: http://192.168.1.191:8080/api/v1/health
```

另一台电脑使用这个 `Login URL` 即可。

## 7. 另一台电脑作为开发端

如果你要在另一台电脑用 VSCode 修改代码，建议按下面步骤配置。

### 7.1 打开项目

在 VSCode 中打开：

```text
G:\LMSNetpj
```

不要只打开 `backend` 或 `frontend-admin` 子目录，否则根目录任务和统一脚本不容易使用。

### 7.2 安装 Node 依赖

```powershell
cd G:\LMSNetpj
npm install
npm --prefix frontend-admin install
npm --prefix desktop-pet install
```

### 7.3 安装 Python 依赖

```powershell
cd G:\LMSNetpj\face-service
python -m venv .venv
.\.venv\Scripts\Activate.ps1
pip install -r requirements.txt
```

### 7.4 配置访问原电脑后端

如果数据库和后端仍运行在原电脑，另一台电脑只运行前端或桌宠，则创建：

```text
G:\LMSNetpj\.env
```

参考：

```text
G:\LMSNetpj\deploy\lan-client.env.example
```

将里面的 `172.16.60.121` 改成原电脑当前局域网 IP，例如：

```text
VITE_BACKEND_BASE_URL=http://192.168.1.191:8080
VITE_PROXY_API_TARGET=http://192.168.1.191:8080
VITE_API_BASE_URL=http://192.168.1.191:8080/api/v1
VITE_LMS_PET_SERVER_URL=http://192.168.1.191:8080
LMS_PET_SERVER_URL=http://192.168.1.191:8080
```

### 7.5 启动访问端前端

```powershell
cd G:\LMSNetpj
.\scripts\dev-start.ps1 -Admin -EnvFile .env -BackendBaseUrl http://原电脑IP:8080
```

然后在另一台电脑打开：

```text
http://127.0.0.1:5173/login
```

此时页面运行在另一台电脑，但数据请求进入原电脑后端和原电脑 MySQL。

### 7.6 启动访问端桌宠

先确保管理端存在对应设备编号，例如 `PC-A02`。

```powershell
cd G:\LMSNetpj
.\scripts\dev-start.ps1 -Pet -PetServerUrl http://原电脑IP:8080 -PetDeviceCode PC-A02
```

桌宠的数据来源仍然是原电脑后端，因此上机时长、余额、消费、低余额提醒和用户桌宠设置都与主系统互通。

## 8. 另一台电脑独立运行整套系统

如果另一台电脑要完全独立运行系统，需要在另一台电脑安装并启动 MySQL，然后执行：

```powershell
mysql -h 127.0.0.1 -P 3306 -u root -p123456 < scripts\init-db.sql
```

再启动：

```powershell
npm run start:lan
```

这种方式会形成另一套独立数据库，不会自动和原电脑数据库同步。除非明确要做独立演示，否则不推荐。

## 9. 另一台电脑运行后端但连接原电脑数据库

该方式适合后端开发测试，但需要原电脑 MySQL 允许局域网访问。

原电脑需要：

- MySQL 监听 `0.0.0.0` 或原电脑局域网 IP。
- Windows 防火墙放行 `3306`。
- 创建远程访问账号，例如 `lms_app`。

另一台电脑启动后端时配置：

```powershell
$env:DB_HOST = "原电脑IP"
$env:DB_PORT = "3306"
$env:DB_NAME = "lms_netcafe"
$env:DB_USER = "lms_app"
$env:DB_PASSWORD = "数据库密码"
.\scripts\dev-start.ps1 -Backend -BindHost 0.0.0.0
```

为了安全，日常使用更推荐只开放原电脑后端 `8080`，不要直接开放 MySQL。

## 10. VSCode 常用任务

在 VSCode 中按：

```text
Ctrl + Shift + P
```

选择：

```text
Tasks: Run Task
```

可看到以下任务：

| 任务 | 作用 |
| --- | --- |
| LMS 一键启动局域网主机 | 启动人脸服务、后端和前端 |
| LMS 后端编译 | Maven 编译后端 |
| LMS 前端构建 | 构建 Vue 网页端 |
| LMS 桌宠构建 | 构建 Electron 桌宠 |
| LMS 生成迁移包 | 生成局域网迁移压缩包 |

如果另一台电脑没有使用 `E:\DevTools`，需要在 `.vscode/tasks.json` 中把 Java、Maven、Node 路径改成另一台电脑实际路径。

## 11. 运行验证

### 11.1 原电脑主机验证

```powershell
Invoke-WebRequest -UseBasicParsing http://原电脑IP:8080/api/v1/health
Invoke-WebRequest -UseBasicParsing http://原电脑IP:5173/login
```

### 11.2 登录验证

| 账号 | 密码 | 角色 |
| --- | --- | --- |
| `admin` | `123456` | 超级管理员 |
| `cashier` | `123456` | 前台人员 |
| `member001` | `123456` | 普通用户 |

### 11.3 数据互通验证

1. 在另一台电脑登录 `cashier`。
2. 给会员 `M0001` 充值。
3. 在原电脑数据库查询 `member_account` 和 `member_account_flow`。
4. 在另一台电脑登录 `member001`，余额应同步变化。
5. 启动桌宠并绑定同一设备，前台开机后桌宠应显示上机状态。

## 12. 常见问题

### 12.1 另一台电脑打不开系统

检查：

- 两台电脑是否同一网络。
- 是否使用了原电脑真实局域网 IP。
- 原电脑是否执行了 `npm run start:lan`。
- Windows 防火墙是否放行 `5173` 和 `8080`。

### 12.2 页面能打开但登录失败

检查：

- 原电脑后端 `8080` 是否正常。
- 访问端 `.env` 是否指向原电脑后端。
- 前端是否误连到了访问端自己的 `127.0.0.1:8080`。
- 原电脑数据库是否启动。

### 12.3 桌宠连接失败

检查：

- 桌宠服务地址是否为 `http://原电脑IP:8080`。
- 设备编号是否已在管理端登记。
- 对应设备是否存在运行中的上机会话。
- 普通用户是否在网页端关闭了桌宠显示。

### 12.4 VSCode 报路径错误

检查：

- `.vscode/tasks.json` 中的 `E:\DevTools` 是否存在。
- 另一台电脑是否安装了 Node、JDK、Maven、Python。
- Python 虚拟环境是否创建在 `face-service/.venv`。

## 13. 迁移结论

本项目迁移到 `G:\LMSNetpj` 后，可以直接作为另一台电脑的开发目录使用。推荐运行方式是：原电脑保留数据库和后端服务，另一台电脑通过浏览器或本地前端连接原电脑后端。这样可以保证所有数据操作最终进入同一套 MySQL，实现会员、设备、订单、计费、服务呼叫和桌宠状态的实时互通。
