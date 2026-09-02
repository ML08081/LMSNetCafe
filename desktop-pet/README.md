# Desktop Pet

Electron + Vue 桌宠客户端。当前版本已实现吾皇猫透明桌宠、设备绑定、客户机令牌、心跳、实时会话轮询、时长/消费/余额展示、低余额及休息提醒、托盘和本地配置。

```powershell
npm install
npm run dev
```

默认连接 `http://127.0.0.1:8080` 并绑定 `PC-A01`。在桌宠设置中可以修改服务地址和设备编号；执行 `npm run dist` 可生成 Windows 安装包。

局域网客户机可以通过环境变量或启动参数预设主机地址：

```powershell
$env:LMS_PET_SERVER_URL = "http://主机IP:8080"
$env:LMS_PET_DEVICE_CODE = "PC-A02"
npm run dev
```

打包后的 Electron 也支持：

```powershell
.\LMS网咖桌宠.exe --server-url=http://主机IP:8080 --device-code=PC-A02
```
