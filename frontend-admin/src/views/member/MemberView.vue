<template>
  <section class="page">
    <div class="page-header">
      <div>
        <h1 class="page-title">会员管理</h1>
        <p class="page-subtitle">维护会员资料、账户余额、人脸档案和消费状态。</p>
      </div>
      <el-button type="primary" @click="openCreate">新增会员</el-button>
    </div>

    <section class="panel">
      <el-form class="filter-bar" inline>
        <el-form-item label="关键词">
          <el-input v-model="filters.keyword" placeholder="会员编号 / 姓名 / 手机号" clearable @keyup.enter="loadMembers" />
        </el-form-item>
        <el-form-item label="状态">
          <el-select v-model="filters.status" clearable placeholder="全部" style="width: 130px">
            <el-option label="正常" value="ACTIVE" />
            <el-option label="冻结" value="FROZEN" />
          </el-select>
        </el-form-item>
        <el-form-item>
          <el-button type="primary" @click="loadMembers">查询</el-button>
          <el-button @click="resetFilters">重置</el-button>
        </el-form-item>
      </el-form>

      <el-table v-loading="loading" :data="members" style="width: 100%">
        <el-table-column prop="memberNo" label="会员编号" width="140" />
        <el-table-column prop="name" label="姓名" width="110" />
        <el-table-column prop="phone" label="手机号" width="150" />
        <el-table-column prop="levelLabel" label="等级" width="100" />
        <el-table-column prop="segment" label="画像分层" min-width="130" />
        <el-table-column label="流失风险" width="110">
          <template #default="{ row }">
            <el-tag :type="riskType(row.churnRisk)">{{ riskLabel(row.churnRisk) }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="balanceLabel" label="余额" width="120" />
        <el-table-column prop="unusedCoupons" label="可用券" width="90" />
        <el-table-column label="人脸档案" width="120">
          <template #default="{ row }">
            <el-tag :type="row.faceEnrolled ? 'success' : 'info'">{{ row.faceEnrolled ? '已录入' : '未录入' }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="状态" width="100">
          <template #default="{ row }">
            <el-tag :type="row.status === 'ACTIVE' ? 'success' : 'danger'">{{ row.statusLabel }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="230">
          <template #default="{ row }">
            <el-button type="primary" link @click="recharge(row)">充值</el-button>
            <el-button type="primary" link @click="openEdit(row)">编辑</el-button>
            <el-button :type="row.status === 'ACTIVE' ? 'danger' : 'success'" link @click="toggleStatus(row)">
              {{ row.status === 'ACTIVE' ? '冻结' : '解冻' }}
            </el-button>
          </template>
        </el-table-column>
      </el-table>
    </section>

    <section class="panel">
      <div class="panel-header">
        <div>
          <h2>用户画像与精细化运营</h2>
          <p class="panel-description">基于上机偏好、消费能力和最近到店时间识别会员分层与流失风险</p>
        </div>
        <el-form class="filter-bar operation-filter" inline>
          <el-form-item label="风险">
            <el-select v-model="operationFilters.churnRisk" style="width: 120px" @change="loadOperationProfiles">
              <el-option label="全部" value="全部" />
              <el-option label="高" value="HIGH" />
              <el-option label="中" value="MEDIUM" />
              <el-option label="低" value="LOW" />
            </el-select>
          </el-form-item>
          <el-form-item>
            <el-button @click="loadOperationProfiles">刷新</el-button>
          </el-form-item>
        </el-form>
      </div>
      <el-table v-loading="operationLoading" :data="operationProfiles" style="width: 100%">
        <el-table-column prop="memberNo" label="会员编号" width="120" />
        <el-table-column prop="name" label="姓名" width="100" />
        <el-table-column prop="segment" label="会员分层" width="140" />
        <el-table-column label="风险" width="90">
          <template #default="{ row }"><el-tag :type="riskType(row.churnRisk)">{{ riskLabel(row.churnRisk) }}</el-tag></template>
        </el-table-column>
        <el-table-column prop="favoriteGames" label="上网偏好" min-width="180" />
        <el-table-column prop="preferredTimeSlot" label="常上机时段" width="150" />
        <el-table-column prop="beveragePreference" label="消费偏好" min-width="150" />
        <el-table-column label="消费能力" width="100">
          <template #default="{ row }">{{ spendingLabel(row.spendingPower) }}</template>
        </el-table-column>
        <el-table-column label="距上次到店" width="110">
          <template #default="{ row }">{{ row.daysSinceLastVisit ?? '-' }} 天</template>
        </el-table-column>
        <el-table-column prop="unusedCoupons" label="可用券" width="90" />
        <el-table-column prop="recommendation" label="运营建议" min-width="220" />
      </el-table>
    </section>

    <el-dialog v-model="dialogVisible" :title="editingId ? '编辑会员' : '新增会员'" width="520px">
      <el-form :model="form" label-width="92px">
        <el-form-item v-if="!editingId" label="会员编号">
          <el-input v-model="form.memberNo" placeholder="留空自动生成" />
        </el-form-item>
        <el-form-item label="姓名" required>
          <el-input v-model="form.name" placeholder="请输入会员姓名" />
        </el-form-item>
        <el-form-item label="手机号" required>
          <el-input v-model="form.phone" placeholder="请输入手机号" />
        </el-form-item>
        <el-form-item label="身份证">
          <el-input v-model="form.idCardNo" placeholder="可填写脱敏编号" />
        </el-form-item>
        <el-form-item label="等级">
          <el-select v-model="form.level" style="width: 100%">
            <el-option label="普通会员" value="NORMAL" />
            <el-option label="VIP 会员" value="VIP" />
          </el-select>
        </el-form-item>
        <el-form-item label="状态">
          <el-radio-group v-model="form.status">
            <el-radio-button label="ACTIVE">正常</el-radio-button>
            <el-radio-button label="FROZEN">冻结</el-radio-button>
          </el-radio-group>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="saving" @click="saveMember">保存</el-button>
      </template>
    </el-dialog>
  </section>
</template>

<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { http } from '../../api/http'

interface ApiResponse<T> {
  data: T
}

const loading = ref(false)
const saving = ref(false)
const operationLoading = ref(false)
const dialogVisible = ref(false)
const editingId = ref<number>()
const members = ref<any[]>([])
const operationProfiles = ref<any[]>([])
const filters = reactive({ keyword: '', status: '' })
const operationFilters = reactive({ churnRisk: '全部' })
const form = reactive({
  memberNo: '',
  name: '',
  phone: '',
  idCardNo: '',
  level: 'NORMAL',
  status: 'ACTIVE'
})

onMounted(async () => { await Promise.all([loadMembers(), loadOperationProfiles()]) })

async function loadMembers() {
  loading.value = true
  try {
    const response = await http.get<ApiResponse<any[]>>('/members', { params: filters })
    members.value = response.data.data.map((item) => ({
      ...item,
      balanceLabel: money(item.balance),
      levelLabel: item.level === 'VIP' ? 'VIP' : '普通',
      statusLabel: item.status === 'ACTIVE' ? '正常' : '冻结'
    }))
  } finally {
    loading.value = false
  }
}

function resetFilters() {
  filters.keyword = ''
  filters.status = ''
  loadMembers()
}

async function loadOperationProfiles() {
  operationLoading.value = true
  try {
    const response = await http.get<ApiResponse<any[]>>('/members/operation/profiles', {
      params: { churnRisk: operationFilters.churnRisk }
    })
    operationProfiles.value = response.data.data
  } finally {
    operationLoading.value = false
  }
}

function openCreate() {
  editingId.value = undefined
  Object.assign(form, {
    memberNo: '',
    name: '',
    phone: '',
    idCardNo: '',
    level: 'NORMAL',
    status: 'ACTIVE'
  })
  dialogVisible.value = true
}

function openEdit(row: any) {
  editingId.value = row.id
  Object.assign(form, {
    memberNo: row.memberNo,
    name: row.name,
    phone: row.phone,
    idCardNo: row.idCardNo ?? '',
    level: row.level,
    status: row.status
  })
  dialogVisible.value = true
}

async function saveMember() {
  if (!form.name || !form.phone) {
    ElMessage.warning('请填写会员姓名和手机号')
    return
  }
  saving.value = true
  try {
    const payload = {
      memberNo: form.memberNo?.trim(),
      name: form.name.trim(),
      phone: form.phone.trim(),
      idCardNo: form.idCardNo?.trim(),
      level: form.level,
      status: form.status
    }
    if (editingId.value) {
      await http.patch(`/members/${editingId.value}`, payload)
    } else {
      await http.post('/members', payload)
    }
    ElMessage.success('会员信息已保存')
    dialogVisible.value = false
    await loadMembers()
  } finally {
    saving.value = false
  }
}

async function recharge(row: any) {
  try {
    const result = await ElMessageBox.prompt(`为 ${row.name} 输入充值金额`, '会员充值', {
      confirmButtonText: '确认充值',
      cancelButtonText: '取消',
      inputPattern: /^\d+(\.\d{1,2})?$/,
      inputErrorMessage: '请输入正确的充值金额'
    })
    await http.post(`/members/${row.id}/recharge`, {
      amount: Number(result.value),
      payMethod: 'CASH',
      remark: '会员管理充值'
    })
    ElMessage.success('充值成功，账户余额已更新')
    await loadMembers()
  } catch (error: any) {
    if (error !== 'cancel') showError(error)
  }
}

async function toggleStatus(row: any) {
  const status = row.status === 'ACTIVE' ? 'FROZEN' : 'ACTIVE'
  await http.patch(`/members/${row.id}/status`, { status })
  ElMessage.success(status === 'ACTIVE' ? '会员已解冻' : '会员已冻结')
  await loadMembers()
}

function money(value: unknown) {
  return `¥${Number(value ?? 0).toFixed(2)}`
}

function showError(error: any) {
  ElMessage.error(error?.response?.data?.message ?? '操作失败')
}

function riskLabel(value: string) {
  return ({ HIGH: '高', MEDIUM: '中', LOW: '低' } as Record<string, string>)[value] || '-'
}

function riskType(value: string) {
  return value === 'HIGH' ? 'danger' : value === 'MEDIUM' ? 'warning' : 'success'
}

function spendingLabel(value: string) {
  return ({ HIGH: '高消费', MEDIUM: '中等', LOW: '低频' } as Record<string, string>)[value] || '-'
}
</script>
