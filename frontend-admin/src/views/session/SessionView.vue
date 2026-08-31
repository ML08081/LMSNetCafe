<template>
  <section class="page">
    <div class="page-header">
      <div>
        <h1 class="page-title">上机记录</h1>
        <p class="page-subtitle">查询当前与历史上机会话，追踪结算状态。</p>
      </div>
      <el-button plain>导出记录</el-button>
    </div>

    <section class="panel">
      <el-form class="filter-bar" inline>
        <el-form-item label="时间">
          <el-date-picker type="daterange" start-placeholder="开始日期" end-placeholder="结束日期" />
        </el-form-item>
        <el-form-item label="状态">
          <el-select placeholder="全部" style="width: 130px">
            <el-option label="进行中" value="RUNNING" />
            <el-option label="已结束" value="ENDED" />
          </el-select>
        </el-form-item>
        <el-form-item>
          <el-button type="primary">查询</el-button>
          <el-button>重置</el-button>
        </el-form-item>
      </el-form>

      <el-table v-loading="loading" :data="sessions" style="width: 100%">
        <el-table-column prop="sessionNo" label="会话编号" width="160" />
        <el-table-column prop="member" label="会员" width="110" />
        <el-table-column prop="device" label="机位" width="110" />
        <el-table-column prop="startAt" label="开始时间" width="170" />
        <el-table-column prop="endAt" label="结束时间" width="170" />
        <el-table-column prop="duration" label="时长" width="100" />
        <el-table-column prop="amount" label="费用" width="100" />
        <el-table-column label="状态">
          <template #default="{ row }">
            <el-tag :type="row.status === '进行中' ? 'primary' : 'success'">{{ row.status }}</el-tag>
          </template>
        </el-table-column>
      </el-table>
    </section>
  </section>
</template>

<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { http } from '../../api/http'

interface ApiResponse<T> {
  data: T
}

const loading = ref(false)
const sessions = ref<any[]>([])

onMounted(async () => {
  loading.value = true
  try {
    const response = await http.get<ApiResponse<any[]>>('/sessions')
    sessions.value = response.data.data.map((item) => ({
      sessionNo: item.sessionNo,
      member: item.memberName,
      device: item.deviceCode,
      startAt: formatDate(item.startAt),
      endAt: formatDate(item.endAt),
      duration: `${item.durationMinutes} 分钟`,
      amount: money(item.finalAmount ?? item.estimatedAmount),
      status: item.status === 'RUNNING' ? '进行中' : '已结束'
    }))
  } finally {
    loading.value = false
  }
})

function money(value: unknown) {
  return `¥${Number(value ?? 0).toFixed(2)}`
}

function formatDate(value: string | null) {
  return value ? value.replace('T', ' ').slice(0, 16) : '-'
}
</script>
