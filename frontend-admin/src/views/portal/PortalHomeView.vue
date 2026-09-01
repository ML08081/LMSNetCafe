<template>
  <section class="page portal-page">
    <div class="page-header portal-welcome">
      <div>
        <span class="portal-kicker">欢迎回来</span>
        <h1 class="page-title">{{ profile.name || auth.user?.realName }}</h1>
        <p class="page-subtitle">会员号 {{ profile.memberNo || '-' }} · {{ levelLabel(profile.level) }}</p>
      </div>
      <el-tag :type="profile.status === 'ACTIVE' ? 'success' : 'danger'">
        {{ profile.status === 'ACTIVE' ? '账户正常' : '账户受限' }}
      </el-tag>
    </div>

    <div v-loading="loading" class="metric-grid portal-metrics">
      <article class="metric-card emphasis">
        <div class="metric-label">账户余额</div>
        <div class="metric-value money">{{ money(profile.balance) }}</div>
        <div class="metric-hint">请前往前台办理充值</div>
      </article>
      <article class="metric-card">
        <div class="metric-label">可用机位</div>
        <div class="metric-value">{{ overview.availableDevices ?? 0 }} 台</div>
        <div class="metric-hint">机位状态来自实时设备表</div>
      </article>
      <article class="metric-card">
        <div class="metric-label">累计充值</div>
        <div class="metric-value">{{ money(profile.totalRecharge) }}</div>
        <div class="metric-hint">全部账户充值记录</div>
      </article>
      <article class="metric-card">
        <div class="metric-label">累计消费</div>
        <div class="metric-value">{{ money(profile.totalConsume) }}</div>
        <div class="metric-hint">全部上机与服务消费</div>
      </article>
    </div>

    <div class="dashboard-grid">
      <section class="panel current-session-panel">
        <div class="panel-header">
          <h2>当前上机</h2>
          <el-tag :type="currentSession ? 'success' : 'info'">{{ currentSession ? '进行中' : '未上机' }}</el-tag>
        </div>
        <div v-if="currentSession" class="session-focus">
          <div>
            <span class="muted">当前机位</span>
            <strong>{{ currentSession.deviceCode }}</strong>
            <small>{{ currentSession.area }} · {{ currentSession.seatNo }}</small>
          </div>
          <div>
            <span class="muted">已上机</span>
            <strong>{{ currentSession.durationMinutes }} 分钟</strong>
            <small>{{ formatDate(currentSession.startAt) }} 开始</small>
          </div>
          <div>
            <span class="muted">当前计费</span>
            <strong>{{ money(currentSession.estimatedAmount) }}</strong>
            <small>最终金额以下机结算为准</small>
          </div>
        </div>
        <el-empty v-else description="当前没有进行中的上机会话" :image-size="72" />
      </section>

      <section class="panel">
        <div class="panel-header"><h2>常用服务</h2></div>
        <div class="portal-actions">
          <button @click="router.push('/portal/services')"><el-icon><ShoppingCart /></el-icon><span>呼叫与点餐</span></button>
          <button @click="router.push('/portal/account')"><el-icon><Wallet /></el-icon><span>余额明细</span></button>
          <button @click="router.push('/portal/devices')"><el-icon><MapLocation /></el-icon><span>查看机位</span></button>
        </div>
      </section>
    </div>

    <section class="panel pet-setting-panel" v-loading="petLoading">
      <div class="panel-header">
        <div><h2>桌面宠物</h2><p class="panel-description">设置会同步到当前上机电脑，最长约 5 秒生效</p></div>
        <el-switch v-model="petSetting.enabled" active-text="显示桌宠" @change="savePetSetting" />
      </div>
      <div class="pet-setting-options" :class="{ disabled: !petSetting.enabled }">
        <label><span><strong>窗口置顶</strong><small>保持桌宠显示在其他窗口上方</small></span><el-switch v-model="petSetting.alwaysOnTop" :disabled="!petSetting.enabled" @change="savePetSetting" /></label>
        <label><span><strong>提示气泡</strong><small>显示余额、时长和休息提醒</small></span><el-switch v-model="petSetting.showBubble" :disabled="!petSetting.enabled" @change="savePetSetting" /></label>
      </div>
    </section>
  </section>
</template>

<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { MapLocation, ShoppingCart, Wallet } from '@element-plus/icons-vue'
import { ElMessage } from 'element-plus'
import { useRouter } from 'vue-router'
import { http } from '../../api/http'
import { useAuthStore } from '../../stores/auth'

interface ApiResponse<T> { data: T }

const router = useRouter()
const auth = useAuthStore()
const loading = ref(false)
const petLoading = ref(false)
const overview = ref<any>({ profile: {}, currentSession: null, availableDevices: 0 })
const petSetting = reactive({ enabled: true, alwaysOnTop: true, showBubble: true })
const profile = computed(() => overview.value.profile ?? {})
const currentSession = computed(() => overview.value.currentSession)

onMounted(async () => {
  loading.value = true
  petLoading.value = true
  try {
    const [response, petResponse] = await Promise.all([
      http.get<ApiResponse<any>>('/portal/overview'),
      http.get<ApiResponse<any>>('/portal/services/pet-settings')
    ])
    overview.value = response.data.data
    Object.assign(petSetting, petResponse.data.data)
  } finally {
    loading.value = false
    petLoading.value = false
  }
})

async function savePetSetting() {
  petLoading.value = true
  try {
    await http.patch('/portal/services/pet-settings', petSetting)
    ElMessage.success('桌宠设置已同步')
  } catch (error: any) {
    ElMessage.error(error.response?.data?.message || '桌宠设置保存失败')
  } finally { petLoading.value = false }
}

function money(value: unknown) { return `¥${Number(value ?? 0).toFixed(2)}` }
function formatDate(value: string) { return value ? value.replace('T', ' ').slice(0, 16) : '-' }
function levelLabel(value: string) { return value === 'VIP' ? 'VIP 会员' : '普通会员' }
</script>
