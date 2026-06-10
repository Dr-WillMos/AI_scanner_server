<script setup lang="ts">
defineProps<{ level: string }>()
</script>

<template>
  <el-tag :type="level === 'HIGH' ? 'danger' : level === 'MEDIUM' ? 'warning' : 'success'" size="small">
    {{ level === 'HIGH' ? '高风险' : level === 'MEDIUM' ? '中风险' : '安全' }}
  </el-tag>
</template>
