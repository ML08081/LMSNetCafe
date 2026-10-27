<template>
  <section class="page portal-page">
    <div class="page-header">
      <div><h1 class="page-title">机位与计费</h1><p class="page-subtitle">查看门店机位实时状态和当前生效的计费规则。</p></div>
      <el-segmented v-model="area" :options="areas" />
    </div>
    <section class="panel">
      <div class="panel-header"><h2>自助上机</h2><el-button @click="load">刷新</el-button></div>
      <p v-if="current">正在使用 {{ current.deviceCode }}，上机时间 {{ current.startAt }}。<el-button type="danger" :loading="submitting" @click="settle">预览并下机</el-button></p>
      <p v-else>登录账号即为本次上机会员，请选择计费规则后点击空闲机位的“上机”。</p>
      <el-select v-model="ruleId" placeholder="选择计费规则" style="width:320px"><el-option v-for="rule in rules" :key="rule.id" :value="rule.id" :label="`${rule.ruleName} · ${money(rule.pricePerHour)}/小时`" /></el-select>
    </section>
    <section class="panel">
      <div class="panel-header"><h2>机位状态</h2><div class="seat-legend"><span><i class="idle" />空闲</span><span><i class="active" />使用中</span><span><i class="fault" />不可用</span></div></div>
      <div v-loading="loading" class="portal-seat-grid">
        <article v-for="device in filteredDevices" :key="device.id" :class="['portal-seat', statusClass(device.status)]">
          <div><strong>{{ device.deviceCode }}</strong><span>{{ device.area }} · {{ device.seatNo }}</span></div>
          <el-tag :type="tagType(device.status)" effect="plain">{{ statusLabel(device.status) }}</el-tag>
          <small>{{ device.roomCapacity || 1 }} 人位 · {{ device.hourlyRateHint ? money(device.hourlyRateHint) + '/小时' : '按规则计费' }}</small>
          <small>{{ device.configDesc }}</small>
          <el-button v-if="device.status === 'IDLE'" type="primary" :disabled="!!current || !ruleId" :loading="submitting" @click="start(device)">上机</el-button>
        </article>
      </div>
    </section>
    <section class="panel">
      <div class="panel-header"><h2>计费规则</h2><el-tag type="info">以下为当前生效规则</el-tag></div>
      <el-table :data="rules" style="width: 100%">
        <el-table-column prop="ruleName" label="规则名称" min-width="180" />
        <el-table-column label="小时单价" width="140"><template #default="{ row }"><strong class="money">{{ money(row.pricePerHour) }}</strong></template></el-table-column>
        <el-table-column label="最低时长" width="140"><template #default="{ row }">{{ row.minMinutes }} 分钟</template></el-table-column>
        <el-table-column label="计费单位" width="140"><template #default="{ row }">每 {{ row.billingUnitMinutes }} 分钟</template></el-table-column>
      </el-table>
    </section>
  </section>
</template>

<script setup lang="ts">
import { computed, onMounted, onBeforeUnmount, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { http } from '../../api/http'
interface ApiResponse<T> { data: T }
const loading = ref(false)
const area = ref('全部')
const current = ref<any>(null), ruleId = ref<number>(), submitting = ref(false)
let timer: ReturnType<typeof setInterval> | undefined
const devices = ref<any[]>([])
const rules = ref<any[]>([])
const areas = computed(() => ['全部', ...Array.from(new Set(devices.value.map((item) => item.area)))])
const filteredDevices = computed(() => area.value === '全部' ? devices.value : devices.value.filter((item) => item.area === area.value))
onMounted(async () => { await load(); timer = setInterval(() => { if (!submitting.value) void load() }, 15000) })
onBeforeUnmount(() => clearInterval(timer))
async function load() {
  loading.value = true
  try {
    const [devicesRes, rulesRes, overviewRes] = await Promise.all([
      http.get<ApiResponse<any[]>>('/portal/devices'),
      http.get<ApiResponse<any[]>>('/portal/billing-rules'),
      http.get<ApiResponse<any>>('/portal/overview')
    ])
    devices.value = devicesRes.data.data
    rules.value = rulesRes.data.data
    current.value = overviewRes.data.data.currentSession
    if (!rules.value.some(r => r.id === ruleId.value)) ruleId.value = rules.value[0]?.id
  } catch (e: any) { ElMessage.error(e.response?.data?.message ?? '机位加载失败') }
  finally { loading.value = false }
}
async function start(device: any) {
  if (submitting.value) return
  submitting.value = true
  try {
    const r = rules.value.find(item => item.id === ruleId.value)
    if (Number(r.pricePerHour) < Number(device.hourlyRateHint ?? 0)) { ElMessage.warning('所选规则低于该区域最低小时价，请选择适用规则'); return }
    const min = Math.ceil(Math.max(1, r.minMinutes) / r.billingUnitMinutes) * r.billingUnitMinutes * Number(r.pricePerHour) / 60
    await ElMessageBox.confirm(`确认使用 ${device.deviceCode}？最低费用 ${money(min)}，每 ${r.billingUnitMinutes} 分钟计费，单价 ${money(r.pricePerHour)}/小时。`, '自助上机')
    await http.post('/portal/sessions/start', { deviceId: device.id, billingRuleId: ruleId.value })
    ElMessage.success('上机成功'); await load()
  } catch (e: any) { if (e !== 'cancel' && e !== 'close') ElMessage.error(e.response?.data?.message ?? '上机失败') }
  finally { submitting.value = false }
}
async function settle() {
  if (submitting.value || !current.value) return
  submitting.value = true
  try {
    const id = current.value.id
    const response = await http.get<ApiResponse<any>>(`/portal/sessions/${id}/preview`)
    await ElMessageBox.confirm(`当前费用 ${money(response.data.data.amount)}，最终金额按确认时的时长计算，确认下机？`, '下机结算')
    const result = await http.post<ApiResponse<any>>(`/portal/sessions/${id}/settle`)
    ElMessage.success(`已下机，本次消费 ${money(result.data.data.amount)}`); await load()
  } catch (e: any) { if (e !== 'cancel' && e !== 'close') ElMessage.error(e.response?.data?.message ?? '下机失败') }
  finally { submitting.value = false }
}
function statusLabel(value: string) { return ({ IDLE: '空闲', IN_USE: '使用中', MAINTENANCE: '维护中', FAULT: '故障' } as Record<string, string>)[value] ?? value }
function statusClass(value: string) { return value === 'IDLE' ? 'is-idle' : value === 'IN_USE' ? 'is-active' : 'is-unavailable' }
function tagType(value: string) { return value === 'IDLE' ? 'success' : value === 'IN_USE' ? 'primary' : 'danger' }
function money(value: unknown) { return `¥${Number(value ?? 0).toFixed(2)}` }
</script>
