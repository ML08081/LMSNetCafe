<template>
  <section class="page portal-page">
    <div class="page-header"><div><h1 class="page-title">故障反馈</h1><p class="page-subtitle">设备问题会同步到超级管理员的维修维护队列。</p></div></div>
    <div class="support-grid">
      <section class="panel">
        <div class="panel-header"><h2>提交设备问题</h2></div>
        <el-form label-position="top" @submit.prevent="submit">
          <el-form-item label="问题机位">
            <el-select v-model="form.deviceId" placeholder="请选择机位" filterable style="width: 100%">
              <el-option v-for="device in devices" :key="device.id" :label="`${device.deviceCode} · ${device.area} ${device.seatNo}`" :value="device.id" />
            </el-select>
          </el-form-item>
          <el-form-item label="问题描述">
            <el-input v-model="form.description" type="textarea" :rows="5" maxlength="200" show-word-limit placeholder="请描述故障现象，例如耳机无声、键盘按键失灵等" />
          </el-form-item>
          <el-button type="primary" native-type="submit" :loading="submitting">提交反馈</el-button>
        </el-form>
      </section>
      <section class="panel">
        <div class="panel-header"><h2>处理进度</h2><el-tag>{{ faults.length }} 条</el-tag></div>
        <el-timeline v-if="faults.length">
          <el-timeline-item v-for="fault in faults" :key="fault.id" :timestamp="formatDate(fault.reportedAt)" :type="fault.status === 'RESOLVED' ? 'success' : 'warning'">
            <div class="fault-item-title"><strong>{{ fault.deviceCode }}</strong><el-tag size="small" :type="fault.status === 'RESOLVED' ? 'success' : 'warning'">{{ fault.status === 'RESOLVED' ? '已处理' : '待处理' }}</el-tag></div>
            <p>{{ fault.description }}</p>
            <small v-if="fault.resultDesc">处理结果：{{ fault.resultDesc }}</small>
          </el-timeline-item>
        </el-timeline>
        <el-empty v-else description="暂无反馈记录" :image-size="72" />
      </section>
    </div>
  </section>
</template>

<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { http } from '../../api/http'
interface ApiResponse<T> { data: T }
const devices = ref<any[]>([])
const faults = ref<any[]>([])
const submitting = ref(false)
const form = reactive<{ deviceId?: number, description: string }>({ description: '' })
onMounted(load)
async function load() {
  const [devicesRes, faultsRes] = await Promise.all([
    http.get<ApiResponse<any[]>>('/portal/devices'),
    http.get<ApiResponse<any[]>>('/portal/faults')
  ])
  devices.value = devicesRes.data.data
  faults.value = faultsRes.data.data
  form.deviceId ??= devices.value.find((item) => item.status === 'FAULT')?.id ?? devices.value[0]?.id
}
async function submit() {
  if (!form.deviceId || !form.description.trim()) {
    ElMessage.warning('请选择机位并填写问题描述')
    return
  }
  submitting.value = true
  try {
    await http.post('/portal/faults', { deviceId: form.deviceId, description: form.description.trim() })
    ElMessage.success('反馈已提交，管理员端已同步')
    form.description = ''
    await load()
  } finally { submitting.value = false }
}
function formatDate(value: string) { return value ? value.replace('T', ' ').slice(0, 16) : '-' }
</script>
