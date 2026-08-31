<template>
  <section>
    <h1 class="page-title">经营看板</h1>
    <div class="metric-grid">
      <el-card v-for="item in metrics" :key="item.label" class="metric-card" shadow="never">
        <div>{{ item.label }}</div>
        <div class="metric-value">{{ item.value }}</div>
      </el-card>
    </div>
  </section>
</template>

<script setup lang="ts">
const metrics = [
  { label: '在线会员', value: 0 },
  { label: '空闲设备', value: 0 },
  { label: '今日营收', value: '¥0.00' },
  { label: '进行中会话', value: 0 }
]
</script>
