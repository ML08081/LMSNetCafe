<template>
  <main class="login-page">
    <section class="login-panel">
      <div>
        <h1>网咖综合管理系统</h1>
        <p>管理员、前台人员和普通用户从同一入口登录，进入各自的工作空间。</p>
      </div>

      <el-form :model="form" label-position="top" @submit.prevent="submit">
        <el-form-item label="账号">
          <el-input v-model="form.username" size="large" autocomplete="username" />
        </el-form-item>
        <el-form-item label="密码">
          <el-input v-model="form.password" size="large" type="password" autocomplete="current-password" show-password />
        </el-form-item>
        <el-button type="primary" size="large" native-type="submit" :loading="loading">登录</el-button>
      </el-form>

      <div class="demo-accounts">
        <button v-for="account in accounts" :key="account.username" type="button" @click="selectAccount(account.username)">
          <strong>{{ account.role }}</strong>
          <span>{{ account.username }}</span>
        </button>
      </div>
      <p class="demo-password">演示密码统一为 123456</p>
    </section>
  </main>
</template>

<script setup lang="ts">
import { reactive, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { useAuthStore } from '../../stores/auth'

const router = useRouter()
const route = useRoute()
const auth = useAuthStore()
const loading = ref(false)

const form = reactive({
  username: 'admin',
  password: '123456'
})

const accounts = [
  { role: '超级管理员', username: 'admin' },
  { role: '前台人员', username: 'cashier' },
  { role: '普通用户', username: 'member001' }
]

function selectAccount(username: string) {
  form.username = username
  form.password = '123456'
}

async function submit() {
  loading.value = true
  try {
    await auth.login(form.username, form.password)
    ElMessage.success('登录成功')
    const redirect = route.query.redirect as string | undefined
    await router.push(redirect || auth.homePath)
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : '登录失败')
  } finally {
    loading.value = false
  }
}
</script>
