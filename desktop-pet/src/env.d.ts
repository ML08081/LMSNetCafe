/// <reference types="vite/client" />

interface PetConfig {
  serverUrl: string
  deviceCode: string
  alwaysOnTop: boolean
}

interface ImportMetaEnv {
  readonly VITE_LMS_PET_SERVER_URL?: string
  readonly VITE_LMS_PET_DEVICE_CODE?: string
}

interface ImportMeta {
  readonly env: ImportMetaEnv
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
