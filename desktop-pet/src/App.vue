<template>
  <main class="pet">
    <div class="avatar">LMS</div>
    <section class="status">
      <span>{{ session.deviceCode }}</span>
      <strong>{{ session.state }}</strong>
    </section>
    <div class="grid">
      <div>
        <span>已用时长</span>
        <strong>{{ session.duration }}</strong>
      </div>
      <div>
        <span>当前消费</span>
        <strong>{{ session.amount }}</strong>
      </div>
    </div>
    <p class="tip">{{ session.tip }}</p>
  </main>
</template>

<script setup lang="ts">
const session = {
  deviceCode: 'CLIENT-001',
  state: '等待上机',
  duration: '00:00',
  amount: '¥0.00',
  tip: '连接后端后将实时显示上机状态、余额提醒和休息提醒。'
}
</script>
