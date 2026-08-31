<template>
  <section class="page">
    <div class="page-header">
      <div>
        <h1 class="page-title">计费规则</h1>
        <p class="page-subtitle">配置每小时单价、最小计费时长和低余额提醒阈值。</p>
      </div>
      <el-tag type="info">前台与管理员共享</el-tag>
    </div>

    <section class="panel">
      <el-table v-loading="loading" :data="rules" style="width: 100%">
        <el-table-column prop="ruleName" label="规则名称" />
        <el-table-column prop="price" label="单价" width="120" />
        <el-table-column prop="minMinutes" label="最小时长" width="120" />
        <el-table-column prop="unit" label="计费单位" width="120" />
        <el-table-column prop="threshold" label="余额提醒" width="120" />
        <el-table-column label="状态" width="100">
          <template #default="{ row }">
            <el-tag :type="row.status === 'ENABLED' ? 'success' : 'info'">{{ row.status === 'ENABLED' ? '启用' : '停用' }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="160">
          <template #default="{ row }">
            <el-button type="primary" link @click="editPrice(row)">调整单价</el-button>
            <el-button :type="row.status === 'ENABLED' ? 'danger' : 'success'" link @click="toggleRule(row)">
              {{ row.status === 'ENABLED' ? '停用' : '启用' }}
            </el-button>
          </template>
        </el-table-column>
      </el-table>
    </section>
  </section>
</template>

<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { http } from '../../api/http'

interface ApiResponse<T> {
  data: T
}

const loading = ref(false)
const rules = ref<any[]>([])

onMounted(load)

async function load() {
  loading.value = true
  try {
    const response = await http.get<ApiResponse<any[]>>('/billing/rules')
    rules.value = response.data.data.map((item) => ({
      ...item,
      price: `¥${Number(item.pricePerHour ?? 0).toFixed(2)}/小时`,
      minMinutes: `${item.minMinutes} 分钟`,
      unit: `${item.billingUnitMinutes} 分钟`,
      threshold: `¥${Number(item.lowBalanceThreshold ?? 0).toFixed(2)}`
    }))
  } finally {
    loading.value = false
  }
}

async function editPrice(row: any) {
  const result = await ElMessageBox.prompt(`调整“${row.ruleName}”的每小时单价`, '调整计费规则', {
    confirmButtonText: '保存', cancelButtonText: '取消', inputValue: String(row.pricePerHour),
    inputPattern: /^\d+(\.\d{1,2})?$/, inputErrorMessage: '请输入正确的金额'
  })
  await updateRule(row, { pricePerHour: Number(result.value) })
  ElMessage.success('计费单价已更新')
  await load()
}

async function toggleRule(row: any) {
  await updateRule(row, { status: row.status === 'ENABLED' ? 'DISABLED' : 'ENABLED' })
  ElMessage.success('计费规则状态已更新')
  await load()
}

async function updateRule(row: any, changes: Record<string, unknown>) {
  await http.patch(`/billing/rules/${row.id}`, {
    pricePerHour: row.pricePerHour,
    lowBalanceThreshold: row.lowBalanceThreshold,
    status: row.status,
    ...changes
  })
}
</script>
