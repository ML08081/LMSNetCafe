<template>
  <el-container class="shell">
    <el-aside width="224px" class="sidebar">
      <div class="brand">LMSNetCafe</div>
      <el-menu router :default-active="$route.path" class="nav">
        <el-menu-item index="/">
          <el-icon><Monitor /></el-icon>
          <span>经营看板</span>
        </el-menu-item>
        <el-menu-item index="/workbench">
          <el-icon><Tickets /></el-icon>
          <span>前台工作台</span>
        </el-menu-item>
      </el-menu>
    </el-aside>
    <el-container>
      <el-header class="topbar">
        <span>网咖综合管理系统</span>
        <el-tag type="success">开发环境</el-tag>
      </el-header>
      <el-main class="content">
        <router-view />
      </el-main>
    </el-container>
  </el-container>
</template>

<script setup lang="ts">
import { Monitor, Tickets } from '@element-plus/icons-vue'
</script>
