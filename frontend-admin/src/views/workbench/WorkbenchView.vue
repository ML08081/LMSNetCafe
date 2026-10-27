
<template>
  <section class="page">
    <div class="page-header">
      <div><h1 class="page-title">前台工作台</h1><p class="page-subtitle">核对身份、机位和费用后上机；会话每 15 秒刷新。</p></div>
      <div><el-button @click="loadWorkbench">刷新</el-button><el-button type="primary" @click="router.push('/members')">会员注册</el-button></div>
    </div>
    <el-alert v-if="diagnosticWarning" :title="diagnosticWarning" type="warning" show-icon :closable="false" />
    <el-alert v-if="error" :title="error" type="error" show-icon :closable="false" />
    <div class="workbench-grid">
      <section class="panel">
        <div class="panel-header"><h2>会员选择</h2><el-tag>{{ selectedMember?.faceEnrolled ? '已录入人脸' : '未录入人脸' }}</el-tag></div>
        <el-select v-model="memberId" filterable clearable placeholder="输入会员编号、姓名或手机号" style="width:100%">
          <el-option v-for="member in members" :key="member.id" :value="member.id" :label="`${member.memberNo} · ${member.name} · ${member.phone}`" />
        </el-select>
        <div class="member-profile">
          <div><span class="muted">会员</span><strong>{{ selectedMember?.name ?? '-' }}</strong></div>
          <div><span class="muted">账户余额</span><strong class="money">{{ money(selectedMember?.balance) }}</strong></div>
          <div><span class="muted">账户状态</span><strong>{{ selectedMember ? selectedMember.status === 'ACTIVE' ? '正常' : '已冻结' : '请选择会员' }}</strong></div>
        </div>
        <div class="action-row">
          <el-button type="primary" :disabled="!selectedMember" @click="recharge(selectedMember)">充值</el-button>
          <el-button @click="router.push('/faces')">录入 / 验证人脸</el-button>
          <el-button @click="router.push('/sessions')">上机记录</el-button>
        </div>
      </section>
      <section class="panel">
        <div class="panel-header"><h2>开机操作</h2><el-tag>{{ selectedRuleLabel }}</el-tag></div>
        <el-form label-width="90px">
          <el-form-item label="目标机位"><el-select v-model="selectedSeat" placeholder="选择空闲机位" style="width:100%">
            <el-option v-for="seat in idleSeats" :key="seat.id" :value="seat.id" :label="`${seat.deviceCode} · ${seat.area}`" />
          </el-select></el-form-item>
          <el-form-item label="计费规则"><el-select v-model="billingRule" style="width:100%">
            <el-option v-for="rule in applicableRules" :key="rule.id" :value="rule.id" :label="`${rule.ruleName} · ${money(rule.pricePerHour)}/小时`" />
          </el-select></el-form-item>
          <el-form-item label="身份核验"><el-switch v-model="faceRequired" active-text="人脸认证" inactive-text="前台人工核验" /></el-form-item>
        </el-form>
        <p v-if="selectedRule">最低 {{ selectedRule.minMinutes }} 分钟，每 {{ selectedRule.billingUnitMinutes }} 分钟计费，最低费用 {{ money(minimumCharge) }}。开机后单价固定。</p>
        <el-alert v-if="runningMember" title="该会员已有进行中的会话，请先下机结算。" type="warning" :closable="false" />
        <el-button type="primary" size="large" :loading="submitting" :disabled="!canStart" @click="prepareStart">开始上机</el-button>
      </section>
    </div>
    <section class="panel">
      <div class="panel-header"><h2>机位选择</h2><el-select v-model="area" style="width:200px"><el-option v-for="item in areas" :key="item" :value="item" /></el-select></div>
      <div class="seat-grid"><button v-for="seat in visibleDevices" :key="seat.id" class="seat-button" :class="seat.status === 'IDLE' ? 'idle' : seat.status === 'IN_USE' ? 'active' : 'fault'" :disabled="seat.status !== 'IDLE'" @click="selectedSeat = seat.id">
        <strong>{{ seat.deviceCode }}{{ selectedSeat === seat.id ? ' ✓' : '' }}</strong><span>{{ seat.area }} · {{ statusLabel(seat.status) }}</span>
      </button></div>
    </section>
    <section class="panel">
      <div class="panel-header"><h2>当前会话</h2><el-tag type="warning">低余额 {{ sessions.filter(s => s.lowBalance).length }} 人</el-tag></div>
      <el-table v-loading="loading" :data="sessions">
        <el-table-column prop="deviceCode" label="机位" width="110" /><el-table-column prop="memberName" label="会员" width="110" />
        <el-table-column label="时长"><template #default="{row}">{{ row.durationMinutes }} 分钟</template></el-table-column>
        <el-table-column label="当前费用"><template #default="{row}">{{ money(row.estimatedAmount) }}</template></el-table-column>
        <el-table-column label="扣费后可用"><template #default="{row}"><el-tag :type="row.lowBalance ? 'danger' : 'success'">{{ money(row.availableBalance) }}</el-tag></template></el-table-column>
        <el-table-column label="核验方式"><template #default="{row}">{{ row.authMethod === 'FACE' ? '人脸认证' : row.authMethod === 'MANUAL' ? '人工核验' : row.authMethod === 'ACCOUNT' ? '账号自助' : '历史记录' }}</template></el-table-column>
        <el-table-column label="操作" width="210"><template #default="{row}">
          <el-button type="primary" link @click="recharge(row)">续费</el-button><el-button type="danger" link :loading="settlingId === row.id" @click="settle(row)">预览并结算</el-button>
        </template></el-table-column>
      </el-table>
    </section>
    <el-dialog v-model="faceDialog" title="核验所选会员并上机" width="520px" destroy-on-close :close-on-click-modal="false" :before-close="closeFace">
      <p>{{ selectedMember?.name }} · {{ devices.find(d => d.id === selectedSeat)?.deviceCode }}。验证通过后自动创建会话。</p>
      <CameraCapture v-if="faceDialog" :disabled="submitting" action-label="拍照验证并上机" @captured="startSession" />
    </el-dialog>
  </section>
</template>
<script setup lang="ts">
import { computed, onMounted, onBeforeUnmount, ref, watch } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { useRouter } from 'vue-router'
import CameraCapture from '../../components/CameraCapture.vue'
import { http } from '../../api/http'
interface ApiResponse<T> { data: T }
const router = useRouter()
const memberId = ref<number>(), selectedSeat = ref<number>(), billingRule = ref<number>()
const faceRequired = ref(true), faceDialog = ref(false), loading = ref(false), submitting = ref(false)
const area = ref('全部'), error = ref(''), diagnosticWarning = ref(''), settlingId = ref<number>()
const members = ref<any[]>([]), devices = ref<any[]>([]), billingRules = ref<any[]>([]), sessions = ref<any[]>([])
const selectedMember = computed(() => members.value.find(m => m.id === memberId.value))
const applicableRules = computed(() => {
  const seat = devices.value.find(d => d.id === selectedSeat.value)
  return billingRules.value.filter(r => Number(r.pricePerHour) >= Number(seat?.hourlyRateHint ?? 0))
})
const selectedRule = computed(() => billingRules.value.find(r => r.id === billingRule.value))
const selectedRuleLabel = computed(() => selectedRule.value?.ruleName ?? '请选择规则')
const minimumCharge = computed(() => {
  const r = selectedRule.value
  return r ? Math.ceil(Math.max(1, r.minMinutes) / r.billingUnitMinutes) * r.billingUnitMinutes * Number(r.pricePerHour) / 60 : 0
})
const runningMember = computed(() => sessions.value.some(s => s.memberId === memberId.value))
const canStart = computed(() => Boolean(selectedMember.value?.status === 'ACTIVE' && selectedSeat.value && selectedRule.value && !runningMember.value))
const areas = computed(() => ['全部', ...new Set(devices.value.map(d => d.area))])
const visibleDevices = computed(() => devices.value.filter(d => area.value === '全部' || d.area === area.value))
const idleSeats = computed(() => visibleDevices.value.filter(d => d.status === 'IDLE'))
watch(applicableRules, rules => { if (!rules.some(r => r.id === billingRule.value)) billingRule.value = rules[0]?.id })
let timer: ReturnType<typeof setInterval> | undefined
onMounted(async () => { await loadWorkbench(); timer = setInterval(() => { if (!submitting.value && !faceDialog.value && !settlingId.value) void loadWorkbench() }, 15000) })
onBeforeUnmount(() => clearInterval(timer))
async function loadWorkbench() {
  if (loading.value) return
  loading.value = true
  try {
    const responses = await Promise.all(['/members', '/devices', '/billing/rules', '/sessions/running', '/sessions/diagnostics'].map(path => http.get<ApiResponse<any>>(path)))
    members.value = responses[0].data.data; devices.value = responses[1].data.data
    billingRules.value = responses[2].data.data.filter((r: any) => r.status === 'ENABLED'); sessions.value = responses[3].data.data
    if (!billingRules.value.some(r => r.id === billingRule.value)) billingRule.value = billingRules.value[0]?.id
    if (!idleSeats.value.some(d => d.id === selectedSeat.value)) selectedSeat.value = idleSeats.value[0]?.id
    const diag = responses[4].data.data
    diagnosticWarning.value = diag.staleSessions.length || diag.inconsistentDevices.length
      ? `检测到 ${diag.staleSessions.length} 个超过24小时的会话、${diag.inconsistentDevices.length} 个状态异常机位，请先核对历史数据；演示请使用新注册会员和空闲机位。` : ''
    error.value = ''
  } catch (e: any) { error.value = e.response?.data?.message ?? '工作台加载失败，请检查后端和数据库连接' }
  finally { loading.value = false }
}
async function recharge(target: any) {
  const id = target?.memberId ?? target?.id
  if (!id) return
  try {
    const result = await ElMessageBox.prompt(`为 ${target.name ?? target.memberName} 输入充值金额`, '会员充值', { inputPattern: /^(?!0+(?:\.0+)?$)\d+(\.\d{1,2})?$/, inputErrorMessage: '请输入大于 0 的金额' })
    await http.post(`/members/${id}/recharge`, { amount: Number(result.value), payMethod: 'CASH', remark: '前台充值' })
    ElMessage.success('充值成功'); await loadWorkbench()
  } catch (e: any) { if (e !== 'cancel' && e !== 'close') ElMessage.error(e.response?.data?.message ?? '充值失败') }
}
async function prepareStart() {
  if (!canStart.value) return
  if (Number(selectedMember.value.balance) < minimumCharge.value) { ElMessage.warning('余额不足支付最低上机费用，请先充值'); return }
  if (faceRequired.value) {
    if (!selectedMember.value.faceEnrolled) { ElMessage.warning('请先绑定会员账号并录入人脸'); return }
    faceDialog.value = true
  } else {
    try { await ElMessageBox.confirm(`已人工核验 ${selectedMember.value.name} 的身份？最低费用 ${money(minimumCharge.value)}`, '人工核验上机'); await startSession() } catch { /* cancelled */ }
  }
}
function closeFace(done: () => void) { if (!submitting.value) done() }
async function startSession(image?: Blob) {
  if (submitting.value) return
  submitting.value = true
  try {
    const payload = { memberId: memberId.value!, deviceId: selectedSeat.value!, billingRuleId: billingRule.value! }
    if (image) {
      const form = new FormData()
      Object.entries(payload).forEach(([key, value]) => form.append(key, String(value)))
      form.append('image', image, 'capture.jpg'); await http.post('/sessions/start-with-face', form)
    } else { await http.post('/sessions/start', { ...payload, faceRequired: false }) }
    faceDialog.value = false; ElMessage.success('上机成功，会话和机位已同步'); await loadWorkbench()
  } catch (e: any) { ElMessage.error(e.response?.data?.message ?? '上机失败，请检查服务状态') }
  finally { submitting.value = false }
}
async function settle(row: any) {
  settlingId.value = row.id
  try {
    const response = await http.get<ApiResponse<any>>(`/sessions/${row.id}/preview`)
    const preview = response.data.data
    await ElMessageBox.confirm(`会员 ${row.memberName}，当前费用 ${money(preview.amount)}，余额 ${money(preview.balance)}。最终金额按确认时的时长计算。`, '下机结算预览')
    const result = await http.post<ApiResponse<any>>(`/sessions/${row.id}/settle`)
    ElMessage.success(`结算完成，消费 ${money(result.data.data.amount)}`); await loadWorkbench()
  } catch (e: any) { if (e !== 'cancel' && e !== 'close') ElMessage.error(e.response?.data?.message ?? '结算失败') }
  finally { settlingId.value = undefined }
}
function money(value: unknown) { return `¥${Number(value ?? 0).toFixed(2)}` }
function statusLabel(value: string) { return ({ IDLE:'空闲', IN_USE:'使用中', FAULT:'故障', MAINTENANCE:'维护', DISABLED:'停用' } as Record<string,string>)[value] ?? value }
</script>
