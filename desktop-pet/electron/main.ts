import { app, BrowserWindow, ipcMain, Menu, nativeImage, screen, Tray } from 'electron'
import fs from 'node:fs'
import path from 'node:path'
import { fileURLToPath } from 'node:url'

const currentDir = path.dirname(fileURLToPath(import.meta.url))
let petWindow: BrowserWindow | null = null
let tray: Tray | null = null
let quitting = false

interface PetConfig { serverUrl: string; deviceCode: string; alwaysOnTop: boolean }
const defaultConfig: PetConfig = { serverUrl: 'http://127.0.0.1:8080', deviceCode: 'PC-A01', alwaysOnTop: true }

function configPath() { return path.join(app.getPath('userData'), 'pet-config.json') }
function readConfig(): PetConfig {
  try { return { ...defaultConfig, ...JSON.parse(fs.readFileSync(configPath(), 'utf8')) } }
  catch { return { ...defaultConfig } }
}
function writeConfig(config: PetConfig) {
  fs.writeFileSync(configPath(), JSON.stringify(config, null, 2), 'utf8')
}

function createWindow() {
  const config = readConfig()
  const area = screen.getPrimaryDisplay().workArea
  petWindow = new BrowserWindow({
    width: 300,
    height: 430,
    x: area.x + area.width - 320,
    y: area.y + area.height - 450,
    minWidth: 270,
    minHeight: 380,
    frame: false,
    resizable: true,
    transparent: true,
    alwaysOnTop: config.alwaysOnTop,
    skipTaskbar: true,
    hasShadow: false,
    webPreferences: {
      preload: path.join(currentDir, 'preload.js'),
      contextIsolation: true,
      nodeIntegration: false,
      backgroundThrottling: false
    }
  })

  if (process.env.VITE_DEV_SERVER_URL) {
    void petWindow.loadURL(process.env.VITE_DEV_SERVER_URL)
  } else {
    void petWindow.loadFile(path.join(currentDir, '../dist/index.html'))
  }
  petWindow.on('close', (event) => {
    if (!quitting) {
      event.preventDefault()
      petWindow?.hide()
    }
  })
}

function createTray() {
  const iconPath = app.isPackaged
    ? path.join(process.resourcesPath, 'wuhuang-cat-v2.png')
    : path.join(currentDir, '../public/wuhuang-cat-v2.png')
  const icon = nativeImage.createFromPath(iconPath).resize({ width: 32, height: 32 })
  tray = new Tray(icon)
  tray.setToolTip('LMS 网咖桌宠')
  tray.setContextMenu(Menu.buildFromTemplate([
    { label: '显示桌宠', click: () => petWindow?.show() },
    { label: '隐藏桌宠', click: () => petWindow?.hide() },
    { label: '重新连接', click: () => petWindow?.webContents.send('pet:reconnect') },
    { type: 'separator' },
    { label: '退出', click: () => { quitting = true; app.quit() } }
  ]))
  tray.on('double-click', () => petWindow?.show())
}

ipcMain.handle('pet:get-config', () => readConfig())
ipcMain.handle('pet:save-config', (_event, config: PetConfig) => {
  writeConfig(config)
  petWindow?.setAlwaysOnTop(config.alwaysOnTop)
})
ipcMain.on('pet:hide', () => petWindow?.hide())
ipcMain.on('pet:set-visibility', (_event, visible: boolean) => visible ? petWindow?.showInactive() : petWindow?.hide())
ipcMain.on('pet:set-always-on-top', (_event, enabled: boolean) => petWindow?.setAlwaysOnTop(enabled))
ipcMain.on('pet:quit', () => { quitting = true; app.quit() })

app.whenReady().then(() => {
  createWindow()
  createTray()
})

app.on('activate', () => petWindow?.show())
app.on('window-all-closed', () => {
  // The tray keeps the desktop pet alive until the user explicitly exits.
})
