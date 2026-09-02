<template>
  <section class="page service-desk-page">
    <div class="page-header">
      <div><h1 class="page-title">服务与订单</h1><p class="page-subtitle">处理会员机位呼叫、点餐订单、陪玩服务和配送进度</p></div>
      <el-button :icon="Refresh" :loading="loading" circle title="刷新" @click="loadAll" />
    </div>

    <div class="metric-grid service-desk-metrics">
      <article class="metric-card"><div class="metric-label">待响应呼叫</div><div class="metric-value">{{ pendingCalls }}</div></article>
      <article class="metric-card"><div class="metric-label">待接订单</div><div class="metric-value">{{ pendingOrders }}</div></article>
      <article class="metric-card"><div class="metric-label">准备与配送中</div><div class="metric-value">{{ activeOrders }}</div></article>
    </div>

    <section class="panel" v-loading="loading">
      <el-tabs v-model="activeTab">
        <el-tab-pane name="calls"><template #label>服务呼叫 <el-badge :value="pendingCalls" :hidden="!pendingCalls" /></template>
          <el-table :data="calls" empty-text="暂无服务呼叫">
            <el-table-column label="状态" width="120"><template #default="scope"><el-tag :type="statusType(scope.row.status)">{{ callStatus(scope.row.status) }}</el-tag></template></el-table-column>
            <el-table-column label="服务" width="130"><template #default="scope">{{ callLabel(scope.row.callType) }}</template></el-table-column>
            <el-table-column prop="deviceCode" label="机位" width="100" />
            <el-table-column label="会员" width="150"><template #default="scope">{{ scope.row.memberName }}<small class="table-secondary">{{ scope.row.memberNo }}</small></template></el-table-column>
            <el-table-column prop="message" label="说明" min-width="180" />
            <el-table-column label="发起时间" width="160"><template #default="scope">{{ formatDate(scope.row.createdAt) }}</template></el-table-column>
            <el-table-column label="处理" width="150" fixed="right"><template #default="scope"><el-select :model-value="scope.row.status" size="small" :disabled="['COMPLETED', 'CANCELLED'].includes(scope.row.status)" @change="(value: string) => updateCall(scope.row, value)"><el-option v-for="item in callOptions(scope.row.status)" :key="item.value" :label="item.label" :value="item.value" /></el-select></template></el-table-column>
          </el-table>
        </el-tab-pane>
        <el-tab-pane name="orders"><template #label>点单/陪玩订单 <el-badge :value="pendingOrders" :hidden="!pendingOrders" /></template>
          <el-table :data="orders" empty-text="暂无点餐订单">
            <el-table-column label="状态" width="120"><template #default="scope"><el-tag :type="statusType(scope.row.status)">{{ orderStatus(scope.row.status) }}</el-tag></template></el-table-column>
            <el-table-column prop="orderNo" label="订单号" width="190" />
            <el-table-column prop="deviceCode" label="配送机位" width="100" />
            <el-table-column label="会员" width="140"><template #default="scope">{{ scope.row.memberName }}<small class="table-secondary">{{ scope.row.memberNo }}</small></template></el-table-column>
            <el-table-column label="商品" min-width="230"><template #default="scope">{{ scope.row.items.map((item: any) => `${item.productName}×${item.quantity}`).join('、') }}</template></el-table-column>
            <el-table-column label="实付" width="90"><template #default="scope">{{ money(scope.row.totalAmount) }}</template></el-table-column>
            <el-table-column prop="remark" label="备注" min-width="130" />
            <el-table-column label="处理" width="150" fixed="right"><template #default="scope"><el-select :model-value="scope.row.status" size="small" :disabled="['COMPLETED', 'CANCELLED'].includes(scope.row.status)" @change="(value: string) => updateOrder(scope.row, value)"><el-option v-for="item in orderOptions(scope.row.status)" :key="item.value" :label="item.label" :value="item.value" /></el-select></template></el-table-column>
          </el-table>
        </el-tab-pane>
      </el-tabs>
    </section>
  </section>
</template>

<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { Refresh } from '@element-plus/icons-vue'
import { ElMessage } from 'element-plus'
import { http } from '../../api/http'

interface ApiResponse<T> { data: T }
const loading = ref(false)
const activeTab = ref('calls')
const calls = ref<any[]>([])
const orders = ref<any[]>([])
const pendingCalls = computed(() => calls.value.filter(item => item.status === 'PENDING').length)
const pendingOrders = computed(() => orders.value.filter(item => item.status === 'PENDING').length)
const activeOrders = computed(() => orders.value.filter(item => ['PREPARING', 'DELIVERING'].includes(item.status)).length)
const callStatusOptions = [{ label: '待响应', value: 'PENDING' }, { label: '处理中', value: 'PROCESSING' }, { label: '已完成', value: 'COMPLETED' }, { label: '已取消', value: 'CANCELLED' }]
const orderStatusOptions = [{ label: '待接单', value: 'PENDING' }, { label: '准备中', value: 'PREPARING' }, { label: '配送中', value: 'DELIVERING' }, { label: '已完成', value: 'COMPLETED' }, { label: '已取消', value: 'CANCELLED' }]

onMounted(loadAll)
async function loadAll() {
  loading.value = true
  try {
    const [callResponse, orderResponse] = await Promise.all([
      http.get<ApiResponse<any[]>>('/frontdesk/services/calls'),
      http.get<ApiResponse<any[]>>('/frontdesk/services/orders')
    ])
    calls.value = callResponse.data.data
    orders.value = orderResponse.data.data
  } finally { loading.value = false }
}
async function updateCall(row: any, status: string) {
  try { await http.patch(`/frontdesk/services/calls/${row.id}/status`, { status }); row.status = status; ElMessage.success('服务状态已更新') }
  catch (error: any) { ElMessage.error(error.response?.data?.message || '更新失败') }
}
async function updateOrder(row: any, status: string) {
  try { await http.patch(`/frontdesk/services/orders/${row.id}/status`, { status }); row.status = status; ElMessage.success('订单状态已更新') }
  catch (error: any) { ElMessage.error(error.response?.data?.message || '更新失败') }
}
function callOptions(status: string) {
  if (status === 'PENDING') return callStatusOptions
  if (status === 'PROCESSING') return callStatusOptions.filter(item => item.value !== 'PENDING')
  return callStatusOptions.filter(item => item.value === status)
}
function orderOptions(status: string) {
  const transitions: Record<string, string[]> = {
    PENDING: ['PENDING', 'PREPARING', 'CANCELLED'],
    PREPARING: ['PREPARING', 'DELIVERING', 'COMPLETED', 'CANCELLED'],
    DELIVERING: ['DELIVERING', 'COMPLETED']
  }
  return orderStatusOptions.filter(item => (transitions[status] || [status]).includes(item.value))
}
function money(value: unknown) { return `¥${Number(value || 0).toFixed(2)}` }
function formatDate(value: string) { return value ? value.replace('T', ' ').slice(0, 16) : '-' }
function callLabel(value: string) { return ({ FRONT_DESK: '呼叫前台', CLEANING: '清洁机位', SUPPLIES: '补充用品', DEVICE_HELP: '设备协助' } as any)[value] || value }
function callStatus(value: string) { return ({ PENDING: '待响应', PROCESSING: '处理中', COMPLETED: '已完成', CANCELLED: '已取消' } as any)[value] || value }
function orderStatus(value: string) { return ({ PENDING: '待接单', PREPARING: '准备/匹配中', DELIVERING: '配送/服务中', COMPLETED: '已完成', CANCELLED: '已取消' } as any)[value] || value }
function statusType(value: string) { return value === 'COMPLETED' ? 'success' : value === 'CANCELLED' ? 'info' : value === 'PENDING' ? 'warning' : 'primary' }
</script>
