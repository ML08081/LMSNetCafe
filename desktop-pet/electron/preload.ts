import { contextBridge, ipcRenderer } from 'electron'

interface PetConfig { serverUrl: string; deviceCode: string; alwaysOnTop: boolean }

contextBridge.exposeInMainWorld('lmsPet', {
  version: '0.2.0',
  getConfig: (): Promise<PetConfig> => ipcRenderer.invoke('pet:get-config'),
  saveConfig: (config: PetConfig): Promise<void> => ipcRenderer.invoke('pet:save-config', config),
  setVisibility: (visible: boolean) => ipcRenderer.send('pet:set-visibility', visible),
  setAlwaysOnTop: (enabled: boolean) => ipcRenderer.send('pet:set-always-on-top', enabled),
  hide: () => ipcRenderer.send('pet:hide'),
  quit: () => ipcRenderer.send('pet:quit'),
  onReconnect: (callback: () => void) => ipcRenderer.on('pet:reconnect', callback)
})
