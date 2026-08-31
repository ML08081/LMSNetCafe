<template>
  <section class="page">
    <div class="page-header">
      <div><h1 class="page-title">维修维护</h1><p class="page-subtitle">集中处理用户反馈和设备故障，处理完成后机位自动恢复为空闲。</p></div>
      <el-button :icon="Refresh" circle title="刷新" @click="load" />
    </div>
    <div class="metric-grid">
      <article class="metric-card"><div class="metric-label">待处理</div><div class="metric-value">{{ openCount }}</div><div class="metric-hint">需要检查的设备故障</div></article>
      <article class="metric-card"><div class="metric-label">已完成</div><div class="metric-value">{{ resolvedCount }}</div><div class="metric-hint">已有维修结果的记录</div></article>
    </div>
    <section class="panel">
      <div class="panel-header"><h2>故障工单</h2><el-tag type="warning">用户反馈实时同步</el-tag></div>
      <el-table v-loading="loading" :data="faults" style="width: 100%" empty-text="暂无故障工单">
        <el-table-column prop="deviceCode" label="设备" width="110" />
        <el-table-column label="位置" width="120"><template #default="{ row }">{{ row.area }} {{ row.seatNo }}</template></el-table-column>
        <el-table-column prop="description" label="问题描述" min-width="240" />
        <el-table-column prop="reportedBy" label="反馈人" width="120" />
        <el-table-column label="反馈时间" width="175"><template #default="{ row }">{{ formatDate(row.reportedAt) }}</template></el-table-column>
        <el-table-column label="状态" width="110"><template #default="{ row }"><el-tag :type="row.status === 'RESOLVED' ? 'success' : 'warning'">{{ row.status === 'RESOLVED' ? '已完成' : '待处理' }}</el-tag></template></el-table-column>
        <el-table-column label="操作" width="120"><template #default="{ row }"><el-button v-if="row.status !== 'RESOLVED'" type="primary" link @click="resolve(row)">完成维修</el-button><span v-else class="muted-inline">{{ row.resultDesc }}</span></template></el-table-column>
      </el-table>
    </section>
  </section>
</template>

<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Refresh } from '@element-plus/icons-vue'
import { http } from '../../api/http'
interface ApiResponse<T> { data: T }
const loading = ref(false)
const faults = ref<any[]>([])
const openCount = computed(() => faults.value.filter((item) => item.status !== 'RESOLVED').length)
const resolvedCount = computed(() => faults.value.filter((item) => item.status === 'RESOLVED').length)
onMounted(load)
async function load() {
  loading.value = true
  try { faults.value = (await http.get<ApiResponse<any[]>>('/maintenance/faults')).data.data }
  finally { loading.value = false }
}
async function resolve(row: any) {
  const result = await ElMessageBox.prompt(`填写 ${row.deviceCode} 的维修结果`, '完成维修', {
    confirmButtonText: '确认完成', cancelButtonText: '取消', inputPattern: /\S+/, inputErrorMessage: '请填写维修结果'
  })
  await http.patch(`/maintenance/faults/${row.id}/resolve`, { resultDesc: result.value })
  ElMessage.success('维修工单已完成，设备状态已恢复')
  await load()
}
function formatDate(value: string) { return value ? value.replace('T', ' ').slice(0, 16) : '-' }
</script>
