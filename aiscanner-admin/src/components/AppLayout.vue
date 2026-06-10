<script setup lang="ts">
import { useRouter } from 'vue-router'
import { useAuthStore } from '../stores/auth'
import { Monitor, DataAnalysis, List, Key, Connection, SwitchButton, Warning } from '@element-plus/icons-vue'

const router = useRouter()
const auth = useAuthStore()

function doLogout() {
  auth.logout()
  router.push('/login')
}
</script>

<template>
  <el-container style="height: 100vh">
    <el-aside width="220px" style="background: #304156">
      <div style="color: #fff; font-size: 20px; font-weight: 700; padding: 20px 16px; text-align: center">
        视盾管理后台
      </div>
      <el-menu
        :default-active="router.currentRoute.value.path"
        background-color="#304156"
        text-color="#bfcbd9"
        active-text-color="#409EFF"
        router
      >
        <el-menu-item index="/dashboard"><el-icon><Monitor /></el-icon> 系统大盘</el-menu-item>
        <el-menu-item index="/detections"><el-icon><List /></el-icon> 检测记录</el-menu-item>
        <el-menu-item index="/blacklist"><el-icon><DataAnalysis /></el-icon> 黑名单管理</el-menu-item>
        <el-menu-item index="/keys"><el-icon><Key /></el-icon> Key 管理</el-menu-item>
        <el-menu-item index="/health"><el-icon><Connection /></el-icon> 服务状态</el-menu-item>
        <el-menu-item index="/dlq"><el-icon><Warning /></el-icon> 死信队列</el-menu-item>
      </el-menu>
    </el-aside>
    <el-container>
      <el-header style="display: flex; align-items: center; justify-content: flex-end; border-bottom: 1px solid #e4e7ed">
        <span style="margin-right: 16px; color: #606266">已登录</span>
        <el-button text @click="doLogout"><el-icon><SwitchButton /></el-icon> 退出</el-button>
      </el-header>
      <el-main style="background: #f0f2f5">
        <router-view />
      </el-main>
    </el-container>
  </el-container>
</template>
