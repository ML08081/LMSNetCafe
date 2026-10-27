<template>
  <section class="page">
    <div class="page-header">
      <div>
        <h1 class="page-title">上机记录</h1>
        <p class="page-subtitle">查询当前与历史上机会话，追踪结算状态。</p>
      </div>
      <el-button plain :disabled="!filtered.length" @click="exportCsv">导出当前结果</el-button>
    </div>

    <section class="panel">
      <el-form class="filter-bar" inline>
        <el-form-item label="时间">
          <el-date-picker v-model="dateRange" value-format="YYYY-MM-DD" type="daterange" start-placeholder="开始日期" end-placeholder="结束日期" />
        </el-form-item>
        <el-form-item label="状态">
          <el-select v-model="status" clearable placeholder="全部" style="width: 130px">
            <el-option label="进行中" value="RUNNING" />
            <el-option label="已结束" value="ENDED" />
          </el-select>
        </el-form-item>
        <el-form-item>
          <el-button type="primary" @click="load">刷新</el-button>
          <el-button @click="dateRange = null; status = ''">重置</el-button>
        </el-form-item>
      </el-form>

      <el-table v-loading="loading" :data="filtered" style="width: 100%">
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
import { computed, onMounted, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { http } from '../../api/http'

interface ApiResponse<T> {
  data: T
}

const loading = ref(false)
const sessions = ref<any[]>([])

const dateRange = ref<[string,string] | null>(null)
const status = ref('')
const filtered = computed(() => sessions.value.filter(item => (!status.value || item.rawStatus === status.value) &&
  (!dateRange.value || (item.startAt.slice(0,10) >= dateRange.value[0] && item.startAt.slice(0,10) <= dateRange.value[1]))))
onMounted(load)
async function load() {
  loading.value = true
  try {
    const response = await http.get<ApiResponse<any[]>>('/sessions')
    sessions.value = response.data.data.map((item) => ({
      rawStatus: item.status,
      sessionNo: item.sessionNo,
      member: item.memberName,
      device: item.deviceCode,
      startAt: formatDate(item.startAt),
      endAt: formatDate(item.endAt),
      duration: `${item.durationMinutes} 分钟`,
      amount: money(item.finalAmount ?? item.estimatedAmount),
      status: item.status === 'RUNNING' ? '进行中' : '已结束'
    }))
  } catch (e: any) { ElMessage.error(e.response?.data?.message ?? '上机记录加载失败') } finally {
    loading.value = false
  }
}

function exportCsv() {
  const columns = ['sessionNo','member','device','startAt','endAt','duration','amount','status']
  const escape = (value: unknown) => {
    let text = String(value ?? '')
    if (/^[=+@\-\t\r]/.test(text)) text = "'" + text
    return '"' + text.replace(/"/g, '""') + '"'
  }
  const lines = [['会话编号','会员','机位','开始时间','结束时间','时长','费用','状态'], ...filtered.value.map(row => columns.map(c => row[c]))]
  const url = URL.createObjectURL(new Blob(['\uFEFF' + lines.map(row => row.map(escape).join(',')).join('\r\n')], {type:'text/csv;charset=utf-8'}))
  const link = document.createElement('a'); link.href = url; link.download = '上机记录.csv'; link.click()
  setTimeout(() => URL.revokeObjectURL(url), 1000)
}

function money(value: unknown) {
  return `¥${Number(value ?? 0).toFixed(2)}`
}

function formatDate(value: string | null) {
  return value ? value.replace('T', ' ').slice(0, 16) : '-'
}
</script>
