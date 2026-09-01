/// <reference types="vite/client" />

interface PetConfig {
  serverUrl: string
  deviceCode: string
  alwaysOnTop: boolean
}

interface Window {
  lmsPet?: {
    version: string
    getConfig: () => Promise<PetConfig>
    saveConfig: (config: PetConfig) => Promise<void>
    setVisibility: (visible: boolean) => void
    setAlwaysOnTop: (enabled: boolean) => void
    hide: () => void
    quit: () => void
    onReconnect: (callback: () => void) => void
  }
}
