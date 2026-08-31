<template>
  <section class="page">
    <div class="page-header">
      <div>
        <h1 class="page-title">人脸认证</h1>
        <p class="page-subtitle">管理会员人脸录入状态和上机验证记录。</p>
      </div>
      <el-button type="primary">开始录入</el-button>
    </div>

    <div class="dashboard-grid">
      <section class="panel">
        <div class="panel-header">
          <h2>录入工作台</h2>
          <el-tag type="info">模拟采集</el-tag>
        </div>
        <div class="capture-box">
          <el-icon><Camera /></el-icon>
          <span>摄像头预览区域</span>
        </div>
        <div class="action-row">
          <el-button type="primary">采集照片</el-button>
          <el-button>质量检测</el-button>
        </div>
      </section>

      <section class="panel">
        <div class="panel-header">
          <h2>验证日志</h2>
          <el-button text type="primary">更多</el-button>
        </div>
        <el-table v-loading="loading" :data="logs" style="width: 100%">
          <el-table-column prop="member" label="会员" width="100" />
          <el-table-column prop="device" label="机位" width="100" />
          <el-table-column prop="similarity" label="相似度" width="100" />
          <el-table-column prop="result" label="结果" />
        </el-table>
      </section>
    </div>
  </section>
</template>

<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { Camera } from '@element-plus/icons-vue'
import { http } from '../../api/http'

interface ApiResponse<T> {
  data: T
}

const loading = ref(false)
const logs = ref<any[]>([])

onMounted(async () => {
  loading.value = true
  try {
    const response = await http.get<ApiResponse<any[]>>('/faces/logs')
    logs.value = response.data.data.map((item) => ({
      member: item.memberName ?? '-',
      device: item.deviceCode ?? '-',
      similarity: item.similarity ?? '-',
      result: item.result === 'PASSED' ? '通过' : '未通过'
    }))
  } finally {
    loading.value = false
  }
})
</script>
