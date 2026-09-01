<template>
  <main class="pet-shell" :class="[`state-${connectionState}`, { 'is-alerting': lowBalance }]">
    <header class="window-bar">
      <span class="connection-dot" :title="connectionLabel" />
      <strong>{{ config.deviceCode }}</strong>
      <div class="window-actions">
        <button type="button" title="设置" @click="settingsOpen = !settingsOpen">⚙</button>
        <button type="button" title="隐藏" @click="hideWindow">−</button>
        <button type="button" title="退出" @click="quitApp">×</button>
      </div>
    </header>

    <section v-if="settingsOpen" class="settings-panel">
      <label>
        <span>服务地址</span>
        <input v-model.trim="draftConfig.serverUrl" type="url" />
      </label>
      <label>
        <span>设备编号</span>
        <input v-model.trim="draftConfig.deviceCode" autocomplete="off" />
      </label>
      <label class="toggle-row">
        <input v-model="draftConfig.alwaysOnTop" type="checkbox" />
        <span>窗口始终置顶</span>
      </label>
      <div class="settings-actions">
        <button type="button" @click="settingsOpen = false">取消</button>
        <button type="button" class="primary" @click="saveSettings">保存并重连</button>
      </div>
    </section>

    <template v-else>
      <div v-if="session.petShowBubble !== false" class="speech-bubble" :class="{ warning: lowBalance }">
        {{ message }}
      </div>

      <button class="cat-stage" type="button" title="查看上机详情" @click="detailOpen = !detailOpen">
        <img :src="catImage" alt="吾皇猫桌宠" draggable="false" />
      </button>

      <section class="session-strip" :class="{ expanded: detailOpen }">
        <div class="session-summary">
          <span>{{ session.active ? session.memberName : connectionLabel }}</span>
          <strong>{{ session.active ? formattedDuration : session.deviceStatus || '待机' }}</strong>
        </div>
        <div v-if="session.active" class="session-details">
          <div><span>当前消费</span><strong>¥{{ money(session.currentAmount) }}</strong></div>
          <div><span>账户余额</span><strong>¥{{ money(session.balance) }}</strong></div>
          <div><span>机位</span><strong>{{ session.seatNo }}</strong></div>
        </div>
      </section>
    </template>
  </main>
</template>

<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, reactive, ref } from 'vue'

interface PetConfig { serverUrl: string; deviceCode: string; alwaysOnTop: boolean }
interface ApiResponse<T> { code: number; message: string; data: T }
interface RegisterResult { clientToken: string; heartbeatSeconds: number }
interface SessionSnapshot {
  active: boolean
  deviceCode: string
  seatNo?: string
  area?: string
  deviceStatus?: string
  sessionId?: number
  memberName?: string
  startedAt?: string
  durationMinutes?: number
  currentAmount?: number
  balance?: number
  lowBalanceThreshold?: number
  lowBalance?: boolean
  petEnabled?: boolean
  petAlwaysOnTop?: boolean
  petShowBubble?: boolean
}

const appVersion = '0.2.0'
const catImage = './wuhuang-cat-v2.png'
const defaultConfig: PetConfig = { serverUrl: 'http://127.0.0.1:8080', deviceCode: 'PC-A01', alwaysOnTop: true }
const config = reactive<PetConfig>({ ...defaultConfig })
const draftConfig = reactive<PetConfig>({ ...defaultConfig })
const session = reactive<SessionSnapshot>({ active: false, deviceCode: defaultConfig.deviceCode })
const connectionState = ref<'connecting' | 'online' | 'offline'>('connecting')
const settingsOpen = ref(false)
const detailOpen = ref(false)
const now = ref(Date.now())
let clientToken = ''
let sessionTimer: number | undefined
let heartbeatTimer: number | undefined
let clockTimer: number | undefined
let remotePetEnabled: boolean | undefined

const connectionLabel = computed(() => ({
  connecting: '正在连接',
  online: '已连接',
  offline: '连接中断'
}[connectionState.value]))

const elapsedMinutes = computed(() => {
  if (!session.active || !session.startedAt) return 0
  return Math.max(session.durationMinutes || 0, Math.floor((now.value - new Date(session.startedAt).getTime()) / 60000))
})

const formattedDuration = computed(() => {
  const minutes = elapsedMinutes.value
  return `${String(Math.floor(minutes / 60)).padStart(2, '0')}:${String(minutes % 60).padStart(2, '0')}`
})

const lowBalance = computed(() => Boolean(session.active && session.lowBalance))
const message = computed(() => {
  if (connectionState.value === 'offline') return '后台暂时联系不上，我会继续重连。'
  if (connectionState.value === 'connecting') return '正在确认这台机位的状态。'
  if (!session.active) return '机位空闲，等你来开黑。'
  if (lowBalance.value) return `余额偏低，只剩 ¥${money(session.balance)}。`
  if (elapsedMinutes.value >= 120) return '已经连续上机两小时，起来活动一下吧。'
  return `${session.memberName}，本次已上机 ${formattedDuration.value}。`
})

async function initialize() {
  const stored = window.lmsPet
    ? await window.lmsPet.getConfig()
    : readWebConfig()
  Object.assign(config, defaultConfig, stored)
  Object.assign(draftConfig, config)
  await connect()
  clockTimer = window.setInterval(() => { now.value = Date.now() }, 1000)
}

async function connect() {
  clearNetworkTimers()
  connectionState.value = 'connecting'
  session.active = false
  try {
    const response = await fetch(`${apiBase()}/api/v1/clients/register`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ deviceCode: config.deviceCode, appVersion })
    })
    const payload = await parseResponse<RegisterResult>(response)
    clientToken = payload.clientToken
    connectionState.value = 'online'
    await Promise.all([loadSession(), heartbeat()])
    sessionTimer = window.setInterval(loadSession, 5000)
    heartbeatTimer = window.setInterval(heartbeat, Math.max(10, payload.heartbeatSeconds) * 1000)
  } catch {
    connectionState.value = 'offline'
    sessionTimer = window.setInterval(connect, 10000)
  }
}

async function loadSession() {
  if (!clientToken) return
  try {
    const response = await fetch(`${apiBase()}/api/v1/clients/${encodeURIComponent(config.deviceCode)}/session`, {
      headers: { 'X-Client-Token': clientToken }
    })
    const payload = await parseResponse<SessionSnapshot>(response)
    Object.assign(session, payload)
    if (payload.petEnabled !== undefined && payload.petEnabled !== remotePetEnabled) {
      remotePetEnabled = payload.petEnabled
      window.lmsPet?.setVisibility(payload.petEnabled)
    }
    if (payload.petAlwaysOnTop !== undefined) {
      window.lmsPet?.setAlwaysOnTop(payload.petAlwaysOnTop)
    }
    connectionState.value = 'online'
  } catch {
    connectionState.value = 'offline'
  }
}

async function heartbeat() {
  if (!clientToken) return
  try {
    const response = await fetch(`${apiBase()}/api/v1/clients/${encodeURIComponent(config.deviceCode)}/heartbeat`, {
      method: 'POST',
      headers: { 'X-Client-Token': clientToken }
    })
    if (!response.ok) throw new Error('heartbeat failed')
  } catch {
    connectionState.value = 'offline'
  }
}

async function parseResponse<T>(response: Response): Promise<T> {
  const payload = await response.json() as ApiResponse<T>
  if (!response.ok || payload.code !== 0) throw new Error(payload.message || 'request failed')
  return payload.data
}

async function saveSettings() {
  Object.assign(config, draftConfig)
  if (window.lmsPet) {
    await window.lmsPet.saveConfig({ ...config })
  } else {
    localStorage.setItem('lms-pet-config', JSON.stringify(config))
  }
  settingsOpen.value = false
  await connect()
}

function readWebConfig(): Partial<PetConfig> {
  try { return JSON.parse(localStorage.getItem('lms-pet-config') || '{}') }
  catch { return {} }
}

function apiBase() { return config.serverUrl.replace(/\/$/, '') }
function money(value?: number) { return Number(value || 0).toFixed(2) }
function hideWindow() { window.lmsPet?.hide() }
function quitApp() { window.lmsPet?.quit() }
function clearNetworkTimers() {
  if (sessionTimer) window.clearInterval(sessionTimer)
  if (heartbeatTimer) window.clearInterval(heartbeatTimer)
  sessionTimer = undefined
  heartbeatTimer = undefined
}

onMounted(() => {
  window.lmsPet?.onReconnect(connect)
  void initialize()
})

onBeforeUnmount(() => {
  clearNetworkTimers()
  if (clockTimer) window.clearInterval(clockTimer)
})
</script>
