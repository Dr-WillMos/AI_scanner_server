<script setup lang="ts">
import { ref, computed, onMounted } from 'vue'
import VChart from 'vue-echarts'
import { use } from 'echarts/core'
import { PieChart, BarChart } from 'echarts/charts'
import { TitleComponent, TooltipComponent, GridComponent, LegendComponent } from 'echarts/components'
import { CanvasRenderer } from 'echarts/renderers'
import { fetchStats, type DashboardStats } from '../api/stats'

use([PieChart, BarChart, TitleComponent, TooltipComponent, GridComponent, LegendComponent, CanvasRenderer])

const stats = ref<DashboardStats | null>(null)
const loading = ref(true)

const pieOption = computed(() => {
  if (!stats.value) return {}
  const d = stats.value.byRiskLevel
  return {
    title: { text: '风险等级分布', left: 'center' },
    tooltip: { trigger: 'item' },
    legend: { bottom: 0 },
    series: [{
      type: 'pie',
      radius: ['40%', '70%'],
      data: [
        { value: d.HIGH || 0, name: '高风险' },
        { value: d.MEDIUM || 0, name: '中风险' },
        { value: d.SAFE || 0, name: '安全' },
        { value: d.BLACKLISTED || 0, name: '黑名单拦截' },
      ],
      emphasis: { itemStyle: { shadowBlur: 10, shadowOffsetX: 0, shadowColor: 'rgba(0,0,0,0.5)' } },
    }],
  }
})

const barOption = computed(() => {
  if (!stats.value) return {}
  const bc = stats.value.blacklistCounts
  return {
    title: { text: '黑名单条目数', left: 'center' },
    tooltip: {},
    xAxis: { type: 'category', data: ['权威', '全局', '临时'] },
    yAxis: { type: 'value', minInterval: 1 },
    series: [{ type: 'bar', data: [bc.authority, bc.global, bc.temp], itemStyle: { color: '#409EFF' } }],
  }
})

onMounted(async () => {
  try {
    stats.value = await fetchStats()
  } finally {
    loading.value = false
  }
})
</script>

<template>
  <div v-loading="loading">
    <el-row :gutter="16" style="margin-bottom: 16px">
      <el-col :span="6">
        <el-card><template #header>检测总量</template>
          <div style="font-size: 32px; font-weight: 700; text-align: center">{{ stats?.totalDetections ?? '-' }}</div>
        </el-card>
      </el-col>
      <el-col :span="6">
        <el-card><template #header>黑名单命中</template>
          <div style="font-size: 32px; font-weight: 700; text-align: center; color: #F56C6C">{{ stats?.blacklistHits ?? '-' }}</div>
        </el-card>
      </el-col>
      <el-col :span="6">
        <el-card><template #header>AI 调用次数</template>
          <div style="font-size: 32px; font-weight: 700; text-align: center">{{ stats?.aiCallCount ?? '-' }}</div>
        </el-card>
      </el-col>
      <el-col :span="6">
        <el-card><template #header>AI 平均耗时</template>
          <div style="font-size: 32px; font-weight: 700; text-align: center">{{ stats ? (stats.aiAvgDurationMs / 1000).toFixed(1) + 's' : '-' }}</div>
        </el-card>
      </el-col>
    </el-row>
    <el-row :gutter="16">
      <el-col :span="12">
        <el-card><VChart :option="pieOption" style="height: 350px" /></el-card>
      </el-col>
      <el-col :span="12">
        <el-card><VChart :option="barOption" style="height: 350px" /></el-card>
      </el-col>
    </el-row>
  </div>
</template>
