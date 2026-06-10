<script setup lang="ts">
import { ref } from 'vue'
import { useRouter } from 'vue-router'
import { useAuthStore } from '../stores/auth'
import { ElMessage } from 'element-plus'
import axios from 'axios'

const router = useRouter()
const auth = useAuthStore()
const apiKey = ref('')
const loading = ref(false)

async function doLogin() {
  if (!apiKey.value.trim()) return
  loading.value = true
  try {
    await axios.get('/api/v1/stats', { headers: { 'X-API-Key': apiKey.value } })
    auth.login(apiKey.value.trim())
    router.push('/dashboard')
  } catch (err: any) {
    const status = err.response?.status
    if (status === 401) {
      ElMessage.error('API Key 无效，请检查后重试')
    } else if (status === 403) {
      ElMessage.error('该 Key 没有管理员权限，请使用 Root Key')
    } else {
      ElMessage.error('无法连接后端服务，请确认后端已启动')
    }
  } finally {
    loading.value = false
  }
}
</script>

<template>
  <div style="display: flex; align-items: center; justify-content: center; height: 100vh; background: #f0f2f5">
    <el-card style="width: 400px">
      <template #header>
        <div style="text-align: center; font-size: 24px; font-weight: 700">视盾管理后台</div>
      </template>
      <el-form @submit.prevent="doLogin">
        <el-form-item label="API Key">
          <el-input v-model="apiKey" type="password" placeholder="请输入管理员 Root Key" show-password />
        </el-form-item>
        <el-form-item>
          <el-button type="primary" @click="doLogin" :loading="loading" style="width: 100%">登录</el-button>
        </el-form-item>
      </el-form>
    </el-card>
  </div>
</template>
