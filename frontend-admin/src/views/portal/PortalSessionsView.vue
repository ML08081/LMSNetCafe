<template>
  <section class="page portal-page">
    <div class="page-header"><div><h1 class="page-title">上机记录</h1><p class="page-subtitle">这里只展示当前登录会员自己的上机会话。</p></div><el-tag type="success">本人数据</el-tag></div>
    <section class="panel">
      <el-table v-loading="loading" :data="sessions" style="width: 100%" empty-text="暂无上机记录">
        <el-table-column prop="sessionNo" label="会话编号" min-width="175" />
        <el-table-column prop="deviceCode" label="机位" width="110" />
        <el-table-column label="开始时间" width="175"><template #default="{ row }">{{ formatDate(row.startAt) }}</template></el-table-column>
        <el-table-column label="结束时间" width="175"><template #default="{ row }">{{ row.endAt ? formatDate(row.endAt) : '-' }}</template></el-table-column>
        <el-table-column label="时长" width="110"><template #default="{ row }">{{ row.durationMinutes }} 分钟</template></el-table-column>
        <el-table-column label="费用" width="120"><template #default="{ row }">{{ money(row.finalAmount ?? row.estimatedAmount) }}</template></el-table-column>
        <el-table-column label="状态" width="110"><template #default="{ row }"><el-tag :type="row.status === 'RUNNING' ? 'success' : 'info'">{{ row.status === 'RUNNING' ? '进行中' : '已结束' }}</el-tag></template></el-table-column>
      </el-table>
    </section>
  </section>
</template>

<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { http } from '../../api/http'
interface ApiResponse<T> { data: T }
const loading = ref(false)
const sessions = ref<any[]>([])
onMounted(async () => {
  loading.value = true
  try { sessions.value = (await http.get<ApiResponse<any[]>>('/portal/sessions')).data.data }
  finally { loading.value = false }
})
function money(value: unknown) { return `¥${Number(value ?? 0).toFixed(2)}` }
function formatDate(value: string) { return value ? value.replace('T', ' ').slice(0, 16) : '-' }
</script>
