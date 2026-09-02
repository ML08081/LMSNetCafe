<template>
  <section class="page portal-page">
    <div class="page-header">
      <div><h1 class="page-title">机位与计费</h1><p class="page-subtitle">查看门店机位实时状态和当前生效的计费规则。</p></div>
      <el-segmented v-model="area" :options="areas" />
    </div>
    <section class="panel">
      <div class="panel-header"><h2>机位状态</h2><div class="seat-legend"><span><i class="idle" />空闲</span><span><i class="active" />使用中</span><span><i class="fault" />不可用</span></div></div>
      <div v-loading="loading" class="portal-seat-grid">
        <article v-for="device in filteredDevices" :key="device.id" :class="['portal-seat', statusClass(device.status)]">
          <div><strong>{{ device.deviceCode }}</strong><span>{{ device.area }} · {{ device.seatNo }}</span></div>
          <el-tag :type="tagType(device.status)" effect="plain">{{ statusLabel(device.status) }}</el-tag>
          <small>{{ device.roomCapacity || 1 }} 人位 · {{ device.hourlyRateHint ? money(device.hourlyRateHint) + '/小时' : '按规则计费' }}</small>
          <small>{{ device.configDesc }}</small>
        </article>
      </div>
    </section>
    <section class="panel">
      <div class="panel-header"><h2>计费规则</h2><el-tag type="info">以下为当前生效规则</el-tag></div>
      <el-table :data="rules" style="width: 100%">
        <el-table-column prop="ruleName" label="规则名称" min-width="180" />
        <el-table-column label="小时单价" width="140"><template #default="{ row }"><strong class="money">{{ money(row.pricePerHour) }}</strong></template></el-table-column>
        <el-table-column label="最低时长" width="140"><template #default="{ row }">{{ row.minMinutes }} 分钟</template></el-table-column>
        <el-table-column label="计费单位" width="140"><template #default="{ row }">每 {{ row.billingUnitMinutes }} 分钟</template></el-table-column>
      </el-table>
    </section>
  </section>
</template>

<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { http } from '../../api/http'
interface ApiResponse<T> { data: T }
const loading = ref(false)
const area = ref('全部')
const devices = ref<any[]>([])
const rules = ref<any[]>([])
const areas = computed(() => ['全部', ...Array.from(new Set(devices.value.map((item) => item.area)))])
const filteredDevices = computed(() => area.value === '全部' ? devices.value : devices.value.filter((item) => item.area === area.value))
onMounted(async () => {
  loading.value = true
  try {
    const [devicesRes, rulesRes] = await Promise.all([
      http.get<ApiResponse<any[]>>('/portal/devices'),
      http.get<ApiResponse<any[]>>('/portal/billing-rules')
    ])
    devices.value = devicesRes.data.data
    rules.value = rulesRes.data.data
  } finally { loading.value = false }
})
function statusLabel(value: string) { return ({ IDLE: '空闲', IN_USE: '使用中', MAINTENANCE: '维护中', FAULT: '故障' } as Record<string, string>)[value] ?? value }
function statusClass(value: string) { return value === 'IDLE' ? 'is-idle' : value === 'IN_USE' ? 'is-active' : 'is-unavailable' }
function tagType(value: string) { return value === 'IDLE' ? 'success' : value === 'IN_USE' ? 'primary' : 'danger' }
function money(value: unknown) { return `¥${Number(value ?? 0).toFixed(2)}` }
</script>
