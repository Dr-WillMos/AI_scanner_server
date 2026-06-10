<script setup lang="ts">
import { ref, onMounted, onUnmounted } from 'vue'
import { fetchHealth, type HealthStatus } from '../api/health'

const health = ref<HealthStatus | null>(null)
const loading = ref(false)
let timer: ReturnType<typeof setInterval> | null = null

async function load() {
  loading.value = true
  try { health.value = await fetchHealth() } finally { loading.value = false }
}

onMounted(() => {
  load()
  timer = setInterval(load, 15000)
})
onUnmounted(() => { if (timer) clearInterval(timer) })
</script>

<template>
  <el-card>
    <template #header>服务健康状态 — 每 15 秒自动刷新</template>
    <el-row :gutter="16" v-loading="loading">
      <el-col :span="8" v-for="comp in ['db', 'redis', 'AI']" :key="comp">
        <el-card>
          <template #header>
            {{ comp === 'db' ? 'MySQL' : comp === 'redis' ? 'Redis' : 'AI 分析服务' }}
          </template>
          <div style="text-align: center">
            <el-tag
              :type="health?.components?.[comp]?.status === 'UP' ? 'success' : 'danger'"
              size="large"
              style="font-size: 20px; padding: 16px 32px"
            >
              {{ health?.components?.[comp]?.status ?? 'UNKNOWN' }}
            </el-tag>
          </div>
        </el-card>
      </el-col>
    </el-row>
  </el-card>
</template>
