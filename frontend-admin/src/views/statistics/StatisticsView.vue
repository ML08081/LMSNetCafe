<template>
  <section class="page">
    <div class="page-header">
      <div>
        <h1 class="page-title">数据统计</h1>
        <p class="page-subtitle">按日期查看营业、充值、消费和机位使用率。</p>
      </div>
      <el-date-picker type="daterange" start-placeholder="开始日期" end-placeholder="结束日期" />
    </div>

    <div v-loading="loading" class="metric-grid">
      <article v-for="item in metrics" :key="item.label" class="metric-card">
        <div class="metric-label">{{ item.label }}</div>
        <div class="metric-value">{{ item.value }}</div>
        <div class="metric-hint">{{ item.hint }}</div>
      </article>
    </div>

    <section class="panel">
      <div class="panel-header">
        <h2>营业明细</h2>
        <el-button plain>导出</el-button>
      </div>
      <el-table :data="rows" style="width: 100%">
        <el-table-column prop="date" label="日期" width="130" />
        <el-table-column prop="recharge" label="充值" width="120" />
        <el-table-column prop="consume" label="消费" width="120" />
        <el-table-column prop="sessions" label="上机次数" width="120" />
        <el-table-column prop="usage" label="机位使用率" />
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

const metrics = computed(() => [
  { label: '营业收入', value: money(summary.value.todayRevenue), hint: '今日上机消费' },
  { label: '充值金额', value: money(summary.value.todayRecharge), hint: '今日会员充值' },
  { label: '上机消费', value: money(summary.value.todayRevenue), hint: `${summary.value.runningSessions ?? 0} 个进行中会话` },
  { label: '空闲机位', value: summary.value.idleDevices ?? 0, hint: `故障 ${summary.value.faultDevices ?? 0} 台` }
])

const rows = computed(() => [
  {
    date: new Date().toISOString().slice(0, 10),
    recharge: money(summary.value.todayRecharge),
    consume: money(summary.value.todayRevenue),
    sessions: summary.value.runningSessions ?? 0,
    usage: `${summary.value.idleDevices ?? 0} 台空闲`
  }
])

onMounted(async () => {
  loading.value = true
  try {
    const response = await http.get<ApiResponse<Record<string, number | string>>>('/statistics/dashboard')
    summary.value = response.data.data
  } finally {
    loading.value = false
  }
})

function money(value: unknown) {
  return `¥${Number(value ?? 0).toFixed(2)}`
}
</script>
