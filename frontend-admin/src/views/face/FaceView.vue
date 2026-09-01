<template>
  <section class="page">
    <div class="page-header">
      <div>
        <h1 class="page-title">人脸认证</h1>
        <p class="page-subtitle">为系统用户录入登录人脸，并查看真实识别结果。</p>
      </div>
      <el-button :icon="Refresh" @click="loadData">刷新</el-button>
    </div>

    <div class="face-workspace">
      <section class="panel face-capture-panel">
        <div class="panel-header">
          <h2>采集工作台</h2>
          <el-tag :type="selected?.enrolled ? 'success' : 'info'">
            {{ selected?.enrolled ? '已录入' : '未录入' }}
          </el-tag>
        </div>
        <el-form label-position="top">
          <el-form-item label="认证用户">
            <el-select v-model="selectedUserId" filterable placeholder="请选择用户" style="width: 100%">
              <el-option
                v-for="user in candidates"
                :key="user.userId"
                :label="`${user.realName} · ${user.roleName} (${user.username})`"
                :value="user.userId"
              />
            </el-select>
          </el-form-item>
          <el-form-item label="采集用途">
            <el-radio-group v-model="operation">
              <el-radio-button value="enroll">{{ selected?.enrolled ? '重新录入' : '首次录入' }}</el-radio-button>
              <el-radio-button value="verify" :disabled="!selected?.enrolled">现场验证</el-radio-button>
            </el-radio-group>
          </el-form-item>
        </el-form>
        <CameraCapture
          :key="captureKey"
          :action-label="operation === 'enroll' ? '拍照并保存档案' : '拍照并验证'"
          @captured="submitCapture"
        />
        <div v-if="selected?.enrolled" class="face-profile-meta">
          <span>录入质量 {{ formatQuality(selected.qualityScore) }}</span>
          <span>{{ formatTime(selected.enrolledAt) }}</span>
          <el-button text type="danger" @click="removeProfile">删除档案</el-button>
        </div>
      </section>

      <section class="panel">
        <div class="panel-header">
          <h2>验证日志</h2>
          <el-tag type="info">最近 100 条</el-tag>
        </div>
        <el-table v-loading="loading" :data="logs" height="560" style="width: 100%">
          <el-table-column label="用户" min-width="150">
            <template #default="scope">
              <strong>{{ scope.row.realName || scope.row.memberName || '未知用户' }}</strong>
              <small class="table-secondary">{{ scope.row.username || '-' }}</small>
            </template>
          </el-table-column>
          <el-table-column prop="deviceCode" label="机位" width="100" />
          <el-table-column label="相似度" width="100">
            <template #default="scope">{{ formatSimilarity(scope.row.similarity) }}</template>
          </el-table-column>
          <el-table-column label="结果" width="90">
            <template #default="scope">
              <el-tag :type="scope.row.result === 'PASSED' ? 'success' : 'danger'">
                {{ scope.row.result === 'PASSED' ? '通过' : '未通过' }}
              </el-tag>
            </template>
          </el-table-column>
          <el-table-column prop="failReason" label="说明" min-width="160" show-overflow-tooltip />
          <el-table-column label="时间" min-width="170">
            <template #default="scope">{{ formatTime(scope.row.createdAt) }}</template>
          </el-table-column>
        </el-table>
      </section>
    </div>
  </section>
</template>

<script setup lang="ts">
import { computed, onMounted, ref, watch } from 'vue'
import { Refresh } from '@element-plus/icons-vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import CameraCapture from '../../components/CameraCapture.vue'
import { http } from '../../api/http'

interface ApiResponse<T> { code: number; message: string; data: T }
interface Candidate {
  userId: number
  username: string
  realName: string
  memberId: number | null
  roleName: string
  enrolled: number | boolean
  qualityScore: number | null
  enrolledAt: string | null
}

const loading = ref(false)
const submitting = ref(false)
const candidates = ref<Candidate[]>([])
const logs = ref<any[]>([])
const selectedUserId = ref<number | null>(null)
const operation = ref<'enroll' | 'verify'>('enroll')
const captureKey = ref(0)
const selected = computed(() => candidates.value.find((item) => item.userId === selectedUserId.value))

watch(selectedUserId, () => {
  operation.value = selected.value?.enrolled ? 'verify' : 'enroll'
  captureKey.value += 1
})

async function loadData() {
  loading.value = true
  try {
    const [candidateResponse, logResponse] = await Promise.all([
      http.get<ApiResponse<Candidate[]>>('/faces/candidates'),
      http.get<ApiResponse<any[]>>('/faces/logs')
    ])
    candidates.value = candidateResponse.data.data
    logs.value = logResponse.data.data
    if (!selectedUserId.value && candidates.value.length) {
      selectedUserId.value = candidates.value[0].userId
    }
  } finally {
    loading.value = false
  }
}

async function submitCapture(image: Blob) {
  if (!selectedUserId.value) {
    ElMessage.warning('请先选择认证用户')
    captureKey.value += 1
    return
  }
  submitting.value = true
  const payload = new FormData()
  payload.append('userId', String(selectedUserId.value))
  payload.append('image', image, 'face-capture.jpg')
  try {
    const response = await http.post<ApiResponse<any>>(`/faces/${operation.value}`, payload)
    if (response.data.code !== 0) {
      throw new Error(response.data.message)
    }
    const result = response.data.data
    if (operation.value === 'verify' && !result.matched) {
      ElMessage.error(`${result.message}（相似度 ${formatSimilarity(result.similarity)}）`)
    } else {
      ElMessage.success(operation.value === 'enroll'
        ? `录入成功，质量评分 ${formatQuality(result.qualityScore)}`
        : `验证通过，相似度 ${formatSimilarity(result.similarity)}`)
    }
    await loadData()
  } catch (error: any) {
    ElMessage.error(error.response?.data?.detail || error.response?.data?.message || error.message || '人脸处理失败')
  } finally {
    submitting.value = false
    captureKey.value += 1
  }
}

async function removeProfile() {
  if (!selectedUserId.value) return
  await ElMessageBox.confirm('删除后该用户将无法通过人脸登录，确认继续？', '删除人脸档案', { type: 'warning' })
  await http.delete(`/faces/${selectedUserId.value}`)
  ElMessage.success('人脸档案已删除')
  await loadData()
}

function formatSimilarity(value: number | null) {
  return value == null ? '-' : `${(Number(value) * 100).toFixed(1)}%`
}

function formatQuality(value: number | null) {
  return value == null ? '-' : Number(value).toFixed(1)
}

function formatTime(value: string | null) {
  return value ? new Date(value).toLocaleString('zh-CN', { hour12: false }) : '-'
}

onMounted(loadData)
</script>
