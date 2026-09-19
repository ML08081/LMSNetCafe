<template>
  <main class="login-page">
    <section class="login-panel" :class="{ 'is-face-mode': loginMode === 'face' }">
      <div>
        <h1>网咖综合管理系统</h1>
        <p>三个角色共用一个入口，登录后按权限进入各自空间。</p>
      </div>

      <el-segmented v-model="loginMode" :options="loginOptions" block />

      <el-form v-if="loginMode === 'password'" :model="form" label-position="top" @submit.prevent="submitPassword">
        <el-form-item label="账号">
          <el-input v-model="form.username" size="large" autocomplete="username" />
        </el-form-item>
        <el-form-item label="密码">
          <el-input v-model="form.password" size="large" type="password" autocomplete="current-password" show-password />
        </el-form-item>
        <el-button type="primary" size="large" native-type="submit" :loading="loading">登录</el-button>
      </el-form>

      <div v-else class="face-login-pane">
        <CameraCapture :key="cameraKey" action-label="拍照并登录" @captured="submitFace" />
        <p class="face-privacy">照片仅用于本次识别，不保存登录抓拍原图。</p>
      </div>

    </section>
  </main>
</template>

<script setup lang="ts">
import { reactive, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import CameraCapture from '../../components/CameraCapture.vue'
import { useAuthStore } from '../../stores/auth'

const router = useRouter()
const route = useRoute()
const auth = useAuthStore()
const loading = ref(false)
const loginMode = ref<'password' | 'face'>('password')
const cameraKey = ref(0)
const loginOptions = [
  { label: '账号密码', value: 'password' },
  { label: '人脸识别', value: 'face' }
]

const form = reactive({ username: '', password: '' })

async function submitPassword() {
  loading.value = true
  try {
    await auth.login(form.username, form.password)
    await finishLogin()
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : '登录失败')
  } finally {
    loading.value = false
  }
}

async function submitFace(image: Blob) {
  loading.value = true
  try {
    await auth.faceLogin(image)
    await finishLogin()
  } catch (error: any) {
    ElMessage.error(error.response?.data?.message || error.message || '人脸识别登录失败')
    cameraKey.value += 1
  } finally {
    loading.value = false
  }
}

async function finishLogin() {
  ElMessage.success(`欢迎回来，${auth.user?.realName}`)
  const redirect = route.query.redirect as string | undefined
  await router.push(redirect || auth.homePath)
}
</script>
