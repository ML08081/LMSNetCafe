<template>
  <el-container class="shell" :class="`role-${auth.primaryRole}`">
    <el-aside width="224px" class="sidebar">
      <div class="brand">
        <el-icon><CoffeeCup /></el-icon>
        <span>{{ brandTitle }}</span>
      </div>
      <el-menu router :default-active="$route.path" class="nav">
        <el-menu-item v-for="item in menus" :key="item.path" :index="item.fullPath">
          <el-icon>
            <component :is="icons[item.icon]" />
          </el-icon>
          <span>{{ item.title }}</span>
        </el-menu-item>
      </el-menu>
    </el-aside>
    <el-container>
      <el-header class="topbar">
        <div>
          <strong>{{ consoleTitle }}</strong>
          <span class="topbar-subtitle">{{ auth.roleLabel }}</span>
        </div>
        <div class="topbar-actions">
          <span class="current-user">{{ auth.user?.realName }}</span>
          <el-tag type="success">开发环境</el-tag>
          <el-button :icon="Refresh" circle />
          <el-button @click="logout">退出</el-button>
        </div>
      </el-header>
      <el-main class="content">
        <router-view />
      </el-main>
    </el-container>
  </el-container>
</template>

<script setup lang="ts">
import { computed } from 'vue'
import { useRouter } from 'vue-router'
import {
  Camera,
  Clock,
  CoffeeCup,
  Coin,
  Cpu,
  DataAnalysis,
  House,
  MapLocation,
  Monitor,
  Refresh,
  Service,
  Setting,
  Tickets,
  Tools,
  TrendCharts,
  Wallet,
  User
} from '@element-plus/icons-vue'
import { moduleRoutes } from '../router'
import { useAuthStore } from '../stores/auth'

const router = useRouter()
const auth = useAuthStore()

const icons = {
  Camera,
  Clock,
  Coin,
  Cpu,
  DataAnalysis,
  House,
  MapLocation,
  Monitor,
  Service,
  Setting,
  Tickets,
  Tools,
  TrendCharts,
  Wallet,
  User
}

const brandTitle = computed(() => auth.hasRole('customer') ? 'LMS 用户中心' : 'LMSNetCafe')
const consoleTitle = computed(() => {
  if (auth.hasRole('customer')) return '我的网咖'
  if (auth.hasRole('front_desk')) return '门店运营工作台'
  return '网咖综合管理控制台'
})

const menus = computed(() =>
  moduleRoutes
    .filter((route) => auth.hasPermission(route.meta.permission as string))
    .map((route) => ({
      path: route.path,
      fullPath: `/${route.path}`.replace(/\/$/, '/'),
      title: route.meta.title as string,
      icon: route.meta.icon as keyof typeof icons
    }))
)

function logout() {
  auth.logout()
  router.push('/login')
}
</script>
