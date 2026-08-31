<template>
  <section class="page">
    <div class="page-header">
      <div>
        <h1 class="page-title">经营看板</h1>
        <p class="page-subtitle">今日门店运营、机位状态和风险提醒集中查看。</p>
      </div>
      <el-date-picker type="date" placeholder="选择日期" />
    </div>

    <div v-loading="loading" class="metric-grid">
      <article v-for="item in metrics" :key="item.label" class="metric-card">
        <div class="metric-label">{{ item.label }}</div>
        <div class="metric-value">{{ item.value }}</div>
        <div class="metric-hint">{{ item.hint }}</div>
      </article>
    </div>

    <div class="dashboard-grid">
      <section class="panel">
        <div class="panel-header">
          <h2>机位状态</h2>
          <el-button text type="primary">查看全部</el-button>
        </div>
        <div class="seat-overview">
          <div v-for="seat in seats" :key="seat.status" class="seat-stat">
            <span class="status-dot" :class="seat.type" />
            <span>{{ seat.status }}</span>
            <strong>{{ seat.count }}</strong>
          </div>
        </div>
      </section>

      <section class="panel">
        <div class="panel-header">
          <h2>实时提醒</h2>
          <el-tag type="warning">{{ alerts.length }} 条</el-tag>
        </div>
        <el-timeline>
          <el-timeline-item v-for="item in alerts" :key="item.title" :timestamp="item.time">
            <strong>{{ item.title }}</strong>
            <p>{{ item.desc }}</p>
          </el-timeline-item>
        </el-timeline>
      </section>
    </div>

    <section class="panel">
      <div class="panel-header">
        <h2>进行中会话</h2>
        <el-button type="primary" plain>刷新</el-button>
      </div>
      <el-table :data="runningSessions" style="width: 100%">
        <el-table-column prop="seat" label="机位" width="110" />
        <el-table-column prop="member" label="会员" width="140" />
        <el-table-column prop="startedAt" label="开始时间" width="180" />
        <el-table-column prop="duration" label="已上机" width="120" />
        <el-table-column prop="amount" label="当前消费" width="120" />
        <el-table-column prop="balance" label="余额" />
        <el-table-column label="状态" width="120">
          <template #default="{ row }">
            <el-tag :type="row.balanceValue < 10 ? 'warning' : 'success'">{{ row.status }}</el-tag>
          </template>
        </el-table-column>
      </el-table>
    </section>
  </section>
</template>

<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { http } from '../../api/http'

interface ApiResponse<T> {
  data: T
}

const loading = ref(false)
const summary = ref<Record<string, number | string>>({})
const devices = ref<any[]>([])
const runningSessions = ref<any[]>([])

const metrics = computed(() => [
  { label: '在线会员', value: summary.value.onlineMembers ?? 0, hint: `低余额 ${summary.value.lowBalanceMembers ?? 0} 人` },
  { label: '空闲设备', value: summary.value.idleDevices ?? 0, hint: `故障 ${summary.value.faultDevices ?? 0} 台` },
  { label: '今日营收', value: money(summary.value.todayRevenue), hint: `充值 ${money(summary.value.todayRecharge)}` },
  { label: '进行中会话', value: summary.value.runningSessions ?? 0, hint: '来自实时会话表' }
])

const seats = computed(() => [
  { status: '空闲', count: countDevice('IDLE'), type: 'idle' },
  { status: '使用中', count: countDevice('IN_USE'), type: 'active' },
  { status: '维护中', count: countDevice('MAINTENANCE'), type: 'maintenance' },
  { status: '故障', count: countDevice('FAULT'), type: 'fault' }
])

const alerts = computed(() => {
  const lowBalance = runningSessions.value
    .filter((item) => Number(item.balance ?? 0) < 10)
    .map((item) => ({
      title: `${item.deviceCode} 余额不足`,
      desc: `会员 ${item.memberName} 当前余额 ${money(item.balance)}`,
      time: '实时'
    }))

  const faultDevices = devices.value
    .filter((item) => item.status === 'FAULT')
    .map((item) => ({
      title: `${item.deviceCode} 故障`,
      desc: `${item.area} ${item.seatNo} 等待维修处理`,
      time: '实时'
    }))

  return [...lowBalance, ...faultDevices]
})

onMounted(loadDashboard)

async function loadDashboard() {
  loading.value = true
  try {
    const [summaryRes, devicesRes, sessionsRes] = await Promise.all([
      http.get<ApiResponse<Record<string, number | string>>>('/statistics/dashboard'),
      http.get<ApiResponse<any[]>>('/devices'),
      http.get<ApiResponse<any[]>>('/sessions/running')
    ])
    summary.value = summaryRes.data.data
    devices.value = devicesRes.data.data
    runningSessions.value = sessionsRes.data.data.map((item) => ({
      seat: item.deviceCode,
      member: item.memberName,
      startedAt: formatDate(item.startAt),
      duration: `${item.durationMinutes} 分钟`,
      amount: money(item.estimatedAmount),
      balance: money(item.balance),
      balanceValue: Number(item.balance ?? 0),
      status: Number(item.balance ?? 0) < 10 ? '低余额' : '正常'
    }))
  } finally {
    loading.value = false
  }
}

function countDevice(status: string) {
  return devices.value.filter((item) => item.status === status).length
}

function money(value: unknown) {
  return `¥${Number(value ?? 0).toFixed(2)}`
}

function formatDate(value: string) {
  return value ? value.replace('T', ' ').slice(0, 16) : '-'
}
</script>
