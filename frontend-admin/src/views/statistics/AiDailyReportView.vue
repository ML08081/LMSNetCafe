<template>
  <section class="page">
    <div class="page-header">
      <div><h1 class="page-title">AI 经营日报</h1><p class="page-subtitle">基于真实消费、充值、上机和故障数据生成分析，保存生成时的统计快照。</p></div>
      <div class="report-actions">
        <el-date-picker v-model="date" value-format="YYYY-MM-DD" type="date" :clearable="false" :disabled="generating" :disabled-date="disabledDate" @change="loadReport" />
        <el-button type="primary" :loading="generating" :disabled="loading || !date" @click="generate">{{ report ? '重新生成' : '生成日报' }}</el-button>
        <el-button :disabled="!report || generating || loading" @click="exportReport">导出日报</el-button>
      </div>
    </div>
    <el-alert v-if="status" :title="status.message" :type="status.configured ? 'success' : 'info'" show-icon :closable="false" />
    <el-alert v-if="error" :title="error" type="error" show-icon :closable="false" />
    <div class="report-history" v-if="history.length"><span>最近日报</span><el-button v-for="item in history.slice(0, 10)" :key="item.reportDate" size="small" :disabled="generating" @click="selectDate(item.reportDate)">{{ String(item.reportDate).slice(0, 10) }}</el-button></div>
    <div v-loading="loading || generating" class="report-body">
      <template v-if="report">
        <div class="report-meta"><el-tag :type="report.mode === 'MODEL' ? 'success' : 'info'">{{ report.mode === 'MODEL' ? 'AI 分析' : '基础统计摘要' }}</el-tag><span>{{ report.date }} · 生成于 {{ formatTime(report.generatedAt) }}</span><span v-if="report.model">{{ report.model }}</span></div>
        <p class="report-notice">{{ report.notice }}</p>
        <div class="metric-grid">
          <article class="metric-card"><div class="metric-label">净消费</div><div class="metric-value money">{{ money(m.netConsumption) }}</div><div class="metric-hint">消费 {{ money(m.grossConsumption) }} − 退款 {{ money(m.refundAmount) }}</div></article>
          <article class="metric-card"><div class="metric-label">充值实收</div><div class="metric-value">{{ money(m.rechargeCash) }}</div><div class="metric-hint">另有赠送 {{ money(m.rechargeGifts) }}，不与消费相加</div></article>
          <article class="metric-card"><div class="metric-label">到店会员 / 新增</div><div class="metric-value">{{ m.visitingMembers }} / {{ m.newMembers }}</div><div class="metric-hint">新开会话 {{ m.startedSessions }} 次 · 已结算 {{ m.settledSessions }} 次</div></article>
          <article class="metric-card"><div class="metric-label">机时利用率（估算）</div><div class="metric-value">{{ m.utilizationPercent }}%</div><div class="metric-hint">依据当前 {{ m.currentDeviceCount }} 个机位计算</div></article>
        </div>
        <div class="dashboard-grid report-grid">
          <section class="panel"><div class="panel-header"><h2>经营分析与建议</h2></div><p class="report-analysis">{{ report.analysis }}</p></section>
          <section class="panel"><div class="panel-header"><h2>统计核对</h2></div>
            <dl class="report-stat-list"><div><dt>上机消费</dt><dd>{{ money(m.machineConsumption) }}</dd></div><div><dt>商品与服务消费</dt><dd>{{ money(m.shopConsumption) }}</dd></div><div><dt>前日净消费</dt><dd>{{ money(m.previousNetConsumption) }}</dd></div><div><dt>较前日变化</dt><dd>{{ m.netChangePercent == null ? '无正向基期，不计算百分比' : `${m.netChangePercent}%` }}</dd></div><div><dt>所选日新增故障</dt><dd>{{ m.newFaults }} 条</dd></div><div><dt>生成时未解决故障</dt><dd>{{ m.openFaultsNow }} 条</dd></div><div><dt>生成时待处理订单</dt><dd>{{ m.pendingOrdersNow }} 笔</dd></div><div><dt>所选日付款订单 / 已取消</dt><dd>{{ m.paidOrders }} / {{ m.cancelledOrders }}</dd></div></dl>
          </section>
        </div>
        <section class="panel"><div class="panel-header"><h2>各区域机时</h2><span class="report-notice">故障和空闲数为生成时状态</span></div>
          <el-table :data="m.areas"><el-table-column prop="area" label="区域" /><el-table-column prop="devices" label="当前机位数" /><el-table-column prop="usedMinutes" label="所选日使用分钟" /><el-table-column label="估算利用率"><template #default="{row}">{{ row.utilizationPercent }}%</template></el-table-column><el-table-column prop="currentFaultDevices" label="当前故障" /><el-table-column prop="currentIdleDevices" label="当前空闲" /></el-table>
        </section>
        <section class="panel"><div class="panel-header"><h2>统计口径与数据范围</h2></div><ul class="report-definitions"><li v-for="definition in m.definitions" :key="definition">{{ definition }}</li></ul></section>
      </template>
      <section v-else-if="!loading && !generating" class="panel"><el-empty :description="error ? '日报暂时无法加载，可刷新或稍后重试' : '该日期还没有日报，点击“生成日报”创建'" /></section>
    </div>
  </section>
</template>
<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { getAiStatus, getDailyReport, getReportHistory, generateDailyReport, type AiStatus, type DailyReport, type ReportHistory } from '../../api/ai'
const today = () => new Intl.DateTimeFormat('en-CA', { timeZone:'Asia/Shanghai', year:'numeric', month:'2-digit', day:'2-digit' }).format(new Date())
const date = ref(today()), status = ref<AiStatus>(), report = ref<DailyReport | null>(null), history = ref<ReportHistory[]>([])
const loading = ref(false), generating = ref(false), error = ref('')
const m = computed(() => report.value!.metrics)
let requestId = 0
onMounted(async () => {
  await Promise.allSettled([loadReport(), loadHistory(), getAiStatus().then(value => { status.value = value })])
})
async function loadHistory() { history.value = await getReportHistory() }
async function loadReport() {
  if (!date.value) return
  const ownId = ++requestId; loading.value = true; error.value = ''; report.value = null
  try { const value = await getDailyReport(date.value); if (ownId === requestId) report.value = value }
  catch (e: any) { if (ownId === requestId) error.value = e.response?.data?.message ?? '日报加载失败，请稍后重试' }
  finally { if (ownId === requestId) loading.value = false }
}
async function generate() {
  if (!date.value || generating.value) return
  if (report.value) { try { await ElMessageBox.confirm('重新生成会更新该日期保存的统计快照与分析，确认继续？', '重新生成日报') } catch { return } }
  generating.value = true; error.value = ''
  try { report.value = await generateDailyReport(date.value); await loadHistory(); ElMessage.success('日报已生成并保存') }
  catch (e: any) { error.value = e.response?.data?.message ?? '生成失败，请稍后重试' }
  finally { generating.value = false }
}
async function selectDate(value: string) { date.value = String(value).slice(0,10); await loadReport() }
function disabledDate(value: Date) { return value.getTime() > Date.now() || value.getTime() < new Date(`${Number(today().slice(0,4)) - 5}${today().slice(4)}T00:00:00`).getTime() }
function money(value: unknown) { return `¥${Number(value ?? 0).toFixed(2)}` }
function formatTime(value: string) { return value ? new Date(value).toLocaleString('zh-CN', { hour12:false }) : '-' }
function exportReport() {
  if (!report.value) return
  const r = report.value, data = r.metrics
  const text = [`${r.date} 网咖经营日报`, `生成时间：${formatTime(r.generatedAt)}\n模式：${r.mode === 'MODEL' ? 'AI 分析' : '基础统计摘要'}`, r.notice,
    `净消费：${money(data.netConsumption)}\n退款：${money(data.refundAmount)}\n充值实收：${money(data.rechargeCash)}\n充值赠送：${money(data.rechargeGifts)}`,
    r.analysis, '统计口径：\n' + data.definitions.join('\n'), '完整统计快照：\n' + JSON.stringify(data,null,2)].join('\n\n')
  const url = URL.createObjectURL(new Blob(['\uFEFF'+text], {type:'text/plain;charset=utf-8'}))
  const link = document.createElement('a'); link.href = url; link.download = `经营日报-${r.date}.txt`; link.click(); setTimeout(() => URL.revokeObjectURL(url),1000)
}
</script>
<style scoped>
.report-actions,.report-history,.report-meta { display:flex; align-items:center; flex-wrap:wrap; gap:10px }
.report-history { color:#6b7684; font-size:13px }.report-history .el-button { margin:0 }
.report-body { display:grid; gap:18px; min-height:200px }.report-meta { color:#738095; font-size:13px }
.report-notice { color:#738095; font-size:13px; line-height:1.8; margin:0 }.report-grid { align-items:start }
.report-analysis { white-space:pre-wrap; overflow-wrap:anywhere; line-height:1.9; font-size:14px; margin:0 }
.report-stat-list { margin:0 }.report-stat-list div { display:flex; justify-content:space-between; gap:15px; padding:12px 0; border-bottom:1px solid #edf0f4; font-size:14px }
.report-stat-list dt { color:#738095 }.report-stat-list dd { margin:0; text-align:right }
.report-definitions { padding-left:20px; color:#667587; line-height:1.9; font-size:13px }
@media(max-width:1100px) { .page-header { align-items:flex-start; flex-direction:column }.report-grid { grid-template-columns:1fr } }
</style>
