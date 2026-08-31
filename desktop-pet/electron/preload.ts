import { contextBridge } from 'electron'

contextBridge.exposeInMainWorld('lmsPet', {
  version: '0.1.0'
})
