<template>
  <section class="page portal-page">
    <div class="page-header">
      <div><h1 class="page-title">我的账户</h1><p class="page-subtitle">查看余额、累计充值和每一笔账户变动。</p></div>
      <el-button :icon="Refresh" circle title="刷新" @click="load" />
    </div>
    <div class="metric-grid">
      <article class="metric-card emphasis"><div class="metric-label">当前余额</div><div class="metric-value money">{{ money(profile.balance) }}</div><div class="metric-hint">余额不足时请联系前台充值</div></article>
      <article class="metric-card"><div class="metric-label">累计充值</div><div class="metric-value">{{ money(profile.totalRecharge) }}</div><div class="metric-hint">历史充值总额</div></article>
      <article class="metric-card"><div class="metric-label">累计消费</div><div class="metric-value">{{ money(profile.totalConsume) }}</div><div class="metric-hint">历史消费总额</div></article>
    </div>
    <section class="panel">
      <div class="panel-header"><h2>账户流水</h2><el-tag>{{ flows.length }} 笔</el-tag></div>
      <el-table v-loading="loading" :data="flows" style="width: 100%" empty-text="暂无账户流水">
        <el-table-column prop="flowNo" label="流水号" min-width="180" />
        <el-table-column label="类型" width="110"><template #default="{ row }"><el-tag :type="row.changeAmount > 0 ? 'success' : 'info'">{{ flowType(row.relatedType) }}</el-tag></template></el-table-column>
        <el-table-column label="金额" width="120"><template #default="{ row }"><strong :class="row.changeAmount > 0 ? 'amount-in' : 'amount-out'">{{ signedMoney(row.changeAmount) }}</strong></template></el-table-column>
        <el-table-column label="变动后余额" width="130"><template #default="{ row }">{{ money(row.balanceAfter) }}</template></el-table-column>
        <el-table-column prop="remark" label="说明" min-width="150" />
        <el-table-column label="时间" width="180"><template #default="{ row }">{{ formatDate(row.createdAt) }}</template></el-table-column>
      </el-table>
    </section>
  </section>
</template>

<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { Refresh } from '@element-plus/icons-vue'
import { http } from '../../api/http'
interface ApiResponse<T> { data: T }
const loading = ref(false)
const profile = ref<any>({})
const flows = ref<any[]>([])
onMounted(load)
async function load() {
  loading.value = true
  try {
    const [overviewRes, flowsRes] = await Promise.all([
      http.get<ApiResponse<any>>('/portal/overview'),
      http.get<ApiResponse<any[]>>('/portal/account-flows')
    ])
    profile.value = overviewRes.data.data.profile
    flows.value = flowsRes.data.data.map((item) => ({ ...item, changeAmount: Number(item.changeAmount) }))
  } finally { loading.value = false }
}
function money(value: unknown) { return `¥${Number(value ?? 0).toFixed(2)}` }
function signedMoney(value: unknown) { const amount = Number(value ?? 0); return `${amount > 0 ? '+' : ''}¥${amount.toFixed(2)}` }
function flowType(value: string) { return value === 'RECHARGE' ? '充值' : value === 'CONSUME' ? '消费' : value }
function formatDate(value: string) { return value ? value.replace('T', ' ').slice(0, 19) : '-' }
</script>
