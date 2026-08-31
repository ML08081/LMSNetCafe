<template>
  <section class="page">
    <div class="page-header">
      <div>
        <h1 class="page-title">{{ canMaintain ? '设备管理' : '设备监控' }}</h1>
        <p class="page-subtitle">{{ canMaintain ? '管理机位配置并掌握实时运行状态。' : '监控全部机位的空闲、使用和故障状态。' }}</p>
      </div>
      <el-button v-if="canMaintain" type="primary" @click="openCreate">新增设备</el-button>
    </div>

    <section class="panel">
      <div class="panel-header">
        <h2>机位地图</h2>
        <el-segmented v-model="filters.area" :options="['全部', 'A区', 'B区', 'VIP区']" @change="loadDevices" />
      </div>
      <div class="seat-grid">
        <button v-for="device in devices" :key="device.deviceCode" class="seat-button" :class="device.statusType">
          <strong>{{ device.deviceCode }}</strong>
          <span>{{ device.statusLabel }}</span>
        </button>
      </div>
    </section>

    <section class="panel">
      <div class="panel-header">
        <h2>设备列表</h2>
        <el-form class="filter-bar" inline>
          <el-form-item label="关键词">
            <el-input v-model="filters.keyword" clearable placeholder="编号 / 座位 / IP" @keyup.enter="loadDevices" />
          </el-form-item>
          <el-form-item label="状态">
            <el-select v-model="filters.status" clearable placeholder="全部" style="width: 130px" @change="loadDevices">
              <el-option label="空闲" value="IDLE" />
              <el-option label="使用中" value="IN_USE" />
              <el-option label="维护中" value="MAINTENANCE" />
              <el-option label="故障" value="FAULT" />
              <el-option label="停用" value="DISABLED" />
            </el-select>
          </el-form-item>
          <el-form-item>
            <el-button type="primary" @click="loadDevices">查询</el-button>
            <el-button @click="resetFilters">重置</el-button>
          </el-form-item>
        </el-form>
      </div>
      <el-table v-loading="loading" :data="devices" style="width: 100%">
        <el-table-column prop="deviceCode" label="设备编号" width="130" />
        <el-table-column prop="area" label="区域" width="100" />
        <el-table-column prop="seatNo" label="座位号" width="100" />
        <el-table-column prop="ipAddress" label="IP 地址" width="150" />
        <el-table-column prop="configDesc" label="配置" />
        <el-table-column label="状态" width="110">
          <template #default="{ row }">
            <el-tag :type="row.tagType">{{ row.statusLabel }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column v-if="canMaintain" label="操作" width="270">
          <template #default="{ row }">
            <el-button type="primary" link @click="openEdit(row)">编辑</el-button>
            <el-button type="warning" link @click="markMaintenance(row)">
              {{ row.rawStatus === 'MAINTENANCE' ? '恢复空闲' : '维护' }}
            </el-button>
            <el-button type="danger" link @click="openFault(row)">故障登记</el-button>
          </template>
        </el-table-column>
      </el-table>
    </section>

    <el-dialog v-model="dialogVisible" :title="editingId ? '编辑设备' : '新增设备'" width="560px">
      <el-form :model="form" label-width="92px">
        <el-form-item label="设备编号" required>
          <el-input v-model="form.deviceCode" placeholder="如 PC-A07" />
        </el-form-item>
        <el-form-item label="区域" required>
          <el-select v-model="form.area" allow-create filterable style="width: 100%">
            <el-option label="A区" value="A区" />
            <el-option label="B区" value="B区" />
            <el-option label="VIP区" value="VIP区" />
          </el-select>
        </el-form-item>
        <el-form-item label="座位号" required>
          <el-input v-model="form.seatNo" placeholder="如 A07" />
        </el-form-item>
        <el-form-item label="IP 地址">
          <el-input v-model="form.ipAddress" placeholder="如 192.168.1.107" />
        </el-form-item>
        <el-form-item label="配置">
          <el-input v-model="form.configDesc" placeholder="显卡 / 内存 / 外设" />
        </el-form-item>
        <el-form-item label="状态">
          <el-select v-model="form.status" style="width: 100%">
            <el-option label="空闲" value="IDLE" />
            <el-option label="维护中" value="MAINTENANCE" />
            <el-option label="故障" value="FAULT" />
            <el-option label="停用" value="DISABLED" />
          </el-select>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="saving" @click="saveDevice">保存</el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="faultVisible" title="故障登记" width="520px">
      <el-form :model="faultForm" label-width="92px">
        <el-form-item label="设备">
          <el-input :model-value="faultTarget?.deviceCode" disabled />
        </el-form-item>
        <el-form-item label="故障类型">
          <el-select v-model="faultForm.faultType" style="width: 100%">
            <el-option label="硬件故障" value="HARDWARE" />
            <el-option label="网络故障" value="NETWORK" />
            <el-option label="外设故障" value="PERIPHERAL" />
            <el-option label="其他问题" value="OTHER" />
          </el-select>
        </el-form-item>
        <el-form-item label="说明" required>
          <el-input v-model="faultForm.description" type="textarea" :rows="4" maxlength="200" show-word-limit />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="faultVisible = false">取消</el-button>
        <el-button type="danger" :loading="saving" @click="submitFault">登记故障</el-button>
      </template>
    </el-dialog>
  </section>
</template>

<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { http } from '../../api/http'
import { useAuthStore } from '../../stores/auth'

interface ApiResponse<T> {
  data: T
}

const filters = reactive({ area: '全部', status: '', keyword: '' })
const loading = ref(false)
const saving = ref(false)
const dialogVisible = ref(false)
const faultVisible = ref(false)
const editingId = ref<number>()
const faultTarget = ref<any>()
const devices = ref<any[]>([])
const auth = useAuthStore()
const canMaintain = computed(() => auth.hasPermission('maintenance:manage'))
const form = reactive({
  deviceCode: '',
  area: 'A区',
  seatNo: '',
  ipAddress: '',
  configDesc: '',
  status: 'IDLE'
})
const faultForm = reactive({
  faultType: 'HARDWARE',
  description: ''
})

const statusMap: Record<string, any> = {
  IDLE: { label: '空闲', statusType: 'idle', tagType: 'success' },
  IN_USE: { label: '使用中', statusType: 'active', tagType: 'primary' },
  MAINTENANCE: { label: '维护中', statusType: 'maintenance', tagType: 'warning' },
  FAULT: { label: '故障', statusType: 'fault', tagType: 'danger' },
  DISABLED: { label: '停用', statusType: 'fault', tagType: 'info' }
}

onMounted(loadDevices)

async function loadDevices() {
  loading.value = true
  try {
    const response = await http.get<ApiResponse<any[]>>('/devices', { params: filters })
    devices.value = response.data.data.map((item) => ({
      ...item,
      rawStatus: item.status,
      statusLabel: statusMap[item.status]?.label ?? item.status,
      statusType: statusMap[item.status]?.statusType ?? 'idle',
      tagType: statusMap[item.status]?.tagType ?? 'info'
    }))
  } finally {
    loading.value = false
  }
}

function resetFilters() {
  filters.area = '全部'
  filters.status = ''
  filters.keyword = ''
  loadDevices()
}

function openCreate() {
  editingId.value = undefined
  Object.assign(form, {
    deviceCode: '',
    area: 'A区',
    seatNo: '',
    ipAddress: '',
    configDesc: '',
    status: 'IDLE'
  })
  dialogVisible.value = true
}

function openEdit(row: any) {
  editingId.value = row.id
  Object.assign(form, {
    deviceCode: row.deviceCode,
    area: row.area,
    seatNo: row.seatNo,
    ipAddress: row.ipAddress ?? '',
    configDesc: row.configDesc ?? '',
    status: row.rawStatus
  })
  dialogVisible.value = true
}

async function saveDevice() {
  if (!form.deviceCode || !form.area || !form.seatNo) {
    ElMessage.warning('请补全设备编号、区域和座位号')
    return
  }
  saving.value = true
  try {
    const payload = {
      deviceCode: form.deviceCode.trim(),
      area: form.area.trim(),
      seatNo: form.seatNo.trim(),
      ipAddress: form.ipAddress?.trim(),
      configDesc: form.configDesc?.trim(),
      status: form.status
    }
    if (editingId.value) {
      await http.patch(`/devices/${editingId.value}`, payload)
    } else {
      await http.post('/devices', payload)
    }
    ElMessage.success('设备信息已保存')
    dialogVisible.value = false
    await loadDevices()
  } finally {
    saving.value = false
  }
}

async function markMaintenance(row: any) {
  const status = row.rawStatus === 'MAINTENANCE' ? 'IDLE' : 'MAINTENANCE'
  await http.patch(`/devices/${row.id}/status`, { status })
  ElMessage.success(status === 'IDLE' ? '设备已恢复空闲' : '设备已切换为维护中')
  await loadDevices()
}

function openFault(row: any) {
  faultTarget.value = row
  faultForm.faultType = 'HARDWARE'
  faultForm.description = ''
  faultVisible.value = true
}

async function submitFault() {
  if (!faultTarget.value || !faultForm.description.trim()) {
    ElMessage.warning('请填写故障说明')
    return
  }
  saving.value = true
  try {
    await http.post(`/devices/${faultTarget.value.id}/faults`, {
      faultType: faultForm.faultType,
      description: faultForm.description.trim()
    })
    ElMessage.success('故障已登记并进入维修队列')
    faultVisible.value = false
    await loadDevices()
  } finally {
    saving.value = false
  }
}
</script>
