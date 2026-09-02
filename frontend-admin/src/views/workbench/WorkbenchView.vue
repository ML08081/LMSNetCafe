<template>
  <section class="page">
    <div class="page-header">
      <div>
        <h1 class="page-title">前台工作台</h1>
        <p class="page-subtitle">完成会员检索、充值、选机、开机与下机结算。</p>
      </div>
      <el-button type="primary" @click="router.push('/members')">会员注册</el-button>
    </div>

    <div class="workbench-grid">
      <section class="panel">
        <div class="panel-header">
          <h2>会员检索</h2>
          <el-tag :type="selectedMember ? 'success' : 'info'">{{ selectedMember ? '已选中' : '待选择' }}</el-tag>
        </div>
        <el-input v-model="keyword" placeholder="会员编号 / 手机号 / 姓名" size="large" clearable @keyup.enter="searchMember">
          <template #append>
            <el-button @click="searchMember">搜索</el-button>
          </template>
        </el-input>
        <div class="member-profile">
          <div>
            <span class="muted">会员</span>
            <strong>{{ selectedMember?.name ?? '-' }}</strong>
          </div>
          <div>
            <span class="muted">编号</span>
            <strong>{{ selectedMember?.memberNo ?? '-' }}</strong>
          </div>
          <div>
            <span class="muted">余额</span>
            <strong class="money">{{ money(selectedMember?.balance) }}</strong>
          </div>
          <div>
            <span class="muted">状态</span>
            <el-tag :type="selectedMember?.status === 'ACTIVE' ? 'success' : 'danger'">
              {{ selectedMember?.status === 'ACTIVE' ? '正常' : '不可用' }}
            </el-tag>
          </div>
        </div>
        <div class="action-row">
          <el-button type="primary" :disabled="!selectedMember" @click="recharge(selectedMember)">充值</el-button>
          <el-button>人脸验证</el-button>
          <el-button>消费记录</el-button>
        </div>
      </section>

      <section class="panel">
        <div class="panel-header">
          <h2>开机操作</h2>
          <el-tag>{{ selectedRuleLabel }}</el-tag>
        </div>
        <el-form label-width="86px">
          <el-form-item label="目标机位">
            <el-select v-model="selectedSeat" placeholder="选择空闲机位" style="width: 100%">
              <el-option v-for="seat in idleSeats" :key="seat.id" :label="`${seat.deviceCode} · ${seat.area} · ${seat.roomCapacity || 1}人`" :value="seat.id" />
            </el-select>
          </el-form-item>
          <el-form-item label="计费规则">
            <el-select v-model="billingRule" style="width: 100%">
              <el-option v-for="rule in billingRules" :key="rule.id" :label="rule.ruleName" :value="rule.id" />
            </el-select>
          </el-form-item>
          <el-form-item label="人脸认证">
            <el-switch v-model="faceRequired" active-text="启用" inactive-text="跳过" />
          </el-form-item>
        </el-form>
        <div class="action-row">
          <el-button type="primary" size="large" :loading="submitting" @click="startSession">开始上机</el-button>
          <el-button size="large" @click="resetSelection">重置</el-button>
        </div>
      </section>
    </div>

    <section class="panel">
      <div class="panel-header">
        <h2>机位选择</h2>
        <el-segmented v-model="area" :options="areaOptions" />
      </div>
      <div class="seat-grid">
        <button v-for="seat in seats" :key="seat.deviceCode" class="seat-button" :class="seat.statusType">
          <strong>{{ seat.deviceCode }}</strong>
          <span>{{ seat.label }} · {{ seat.roomCapacity || 1 }}人</span>
        </button>
      </div>
    </section>

    <section class="panel">
      <div class="panel-header">
        <h2>当前会话</h2>
        <el-button type="danger" plain>批量提醒</el-button>
      </div>
      <el-table v-loading="loading" :data="sessions" style="width: 100%">
        <el-table-column prop="seat" label="机位" width="110" />
        <el-table-column prop="member" label="会员" width="120" />
        <el-table-column prop="duration" label="时长" width="110" />
        <el-table-column prop="amount" label="消费" width="110" />
        <el-table-column prop="balance" label="余额" width="110" />
        <el-table-column label="操作" width="220">
          <template #default="{ row }">
            <el-button type="primary" link @click="recharge(row)">续费</el-button>
            <el-button type="danger" link :loading="row.settling" @click="settle(row)">下机结算</el-button>
          </template>
        </el-table-column>
      </el-table>
    </section>
  </section>
</template>

<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { useRouter } from 'vue-router'
import { http } from '../../api/http'

interface ApiResponse<T> {
  data: T
}

const keyword = ref('M0001')
const selectedSeat = ref<number>()
const billingRule = ref<number>()
const faceRequired = ref(true)
const area = ref('全部')
const loading = ref(false)
const submitting = ref(false)
const members = ref<any[]>([])
const devices = ref<any[]>([])
const billingRules = ref<any[]>([])
const sessions = ref<any[]>([])
const router = useRouter()

const selectedMember = computed(() => {
  const value = keyword.value.trim().toLowerCase()
  if (!value) return undefined
  return members.value.find((item) =>
    [item.memberNo, item.phone, item.name].some((field) => String(field).toLowerCase().includes(value))
  )
})

const visibleDevices = computed(() => area.value === '全部'
  ? devices.value
  : devices.value.filter((item) => item.area === area.value))

const idleSeats = computed(() => visibleDevices.value.filter((item) => item.rawStatus === 'IDLE'))

const seats = computed(() => visibleDevices.value)
const areaOptions = computed(() => ['全部', ...Array.from(new Set(devices.value.map((item) => item.area)))])

const selectedRuleLabel = computed(() => {
  const rule = billingRules.value.find((item) => item.id === billingRule.value)
  return rule ? `${rule.ruleName} ¥${Number(rule.pricePerHour).toFixed(2)}/小时` : '请选择计费规则'
})

const statusMap: Record<string, any> = {
  IDLE: { label: '空闲', statusType: 'idle' },
  IN_USE: { label: '使用中', statusType: 'active' },
  MAINTENANCE: { label: '维护', statusType: 'maintenance' },
  FAULT: { label: '故障', statusType: 'fault' }
}

onMounted(loadWorkbench)

async function loadWorkbench() {
  loading.value = true
  try {
    const [membersRes, devicesRes, rulesRes, sessionsRes] = await Promise.all([
      http.get<ApiResponse<any[]>>('/members'),
      http.get<ApiResponse<any[]>>('/devices'),
      http.get<ApiResponse<any[]>>('/billing/rules'),
      http.get<ApiResponse<any[]>>('/sessions/running')
    ])
    members.value = membersRes.data.data
    devices.value = devicesRes.data.data.map((item) => ({
      ...item,
      rawStatus: item.status,
      label: statusMap[item.status]?.label ?? item.status,
      statusType: statusMap[item.status]?.statusType ?? 'idle'
    }))
    billingRules.value = rulesRes.data.data.filter((item) => item.status === 'ENABLED')
    billingRule.value = billingRules.value[0]?.id
    selectedSeat.value = idleSeats.value[0]?.id
    sessions.value = sessionsRes.data.data.map((item) => ({
      id: item.id,
      memberId: item.memberId,
      seat: item.deviceCode,
      member: item.memberName,
      duration: `${item.durationMinutes} 分钟`,
      amount: money(item.estimatedAmount),
      balance: money(item.balance),
      settling: false
    }))
  } finally {
    loading.value = false
  }
}

function searchMember() {
  keyword.value = keyword.value.trim()
}

async function recharge(target: any) {
  const memberId = target?.id ?? target?.memberId
  if (!memberId) return
  try {
    const result = await ElMessageBox.prompt(`为 ${target.name ?? target.member} 输入充值金额`, '会员充值', {
      confirmButtonText: '确认充值',
      cancelButtonText: '取消',
      inputPattern: /^\d+(\.\d{1,2})?$/,
      inputErrorMessage: '请输入正确的充值金额'
    })
    await http.post(`/members/${memberId}/recharge`, {
      amount: Number(result.value),
      payMethod: 'CASH',
      remark: '前台充值'
    })
    ElMessage.success('充值成功，会员余额已更新')
    await loadWorkbench()
  } catch (error: any) {
    if (error !== 'cancel') ElMessage.error(error?.response?.data?.message ?? '充值失败')
  }
}

async function startSession() {
  if (!selectedMember.value || !selectedSeat.value || !billingRule.value) {
    ElMessage.warning('请先选择会员、空闲机位和计费规则')
    return
  }
  submitting.value = true
  try {
    await http.post('/sessions/start', {
      memberId: selectedMember.value.id,
      deviceId: selectedSeat.value,
      billingRuleId: billingRule.value
    })
    ElMessage.success('开机成功，设备和会员会话已同步')
    await loadWorkbench()
  } catch (error: any) {
    ElMessage.error(error?.response?.data?.message ?? '开机失败')
  } finally { submitting.value = false }
}

async function settle(row: any) {
  row.settling = true
  try {
    const response = await http.post<ApiResponse<any>>(`/sessions/${row.id}/settle`)
    ElMessage.success(`结算完成，本次消费 ${money(response.data.data.amount)}`)
    await loadWorkbench()
  } catch (error: any) {
    ElMessage.error(error?.response?.data?.message ?? '结算失败')
  } finally { row.settling = false }
}

function resetSelection() {
  selectedSeat.value = idleSeats.value[0]?.id
  billingRule.value = billingRules.value[0]?.id
  faceRequired.value = true
}

function money(value: unknown) {
  return `¥${Number(value ?? 0).toFixed(2)}`
}
</script>
