<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { fetchDetections, type HistoryItem } from '../api/detections'
import RiskBadge from '../components/RiskBadge.vue'

const records = ref<HistoryItem[]>([])
const total = ref(0)
const page = ref(1)
const size = ref(20)
const loading = ref(false)

const deviceId = ref('')
const authorId = ref('')
const riskLevel = ref('')
const startDate = ref('')
const endDate = ref('')

async function load() {
  if (!deviceId.value.trim()) return
  loading.value = true
  try {
    const res = await fetchDetections({
      deviceId: deviceId.value,
      authorId: authorId.value || undefined,
      riskLevel: riskLevel.value || undefined,
      startDate: startDate.value || undefined,
      endDate: endDate.value || undefined,
      page: page.value,
      size: size.value,
    })
    records.value = res.records
    total.value = res.total
  } finally {
    loading.value = false
  }
}

function onPageChange(p: number) { page.value = p; load() }
function onSizeChange(s: number) { size.value = s; page.value = 1; load() }
function formatDate(d: string) { return d.replace('T', ' ') }

onMounted(load)
</script>

<template>
  <div>
    <el-card style="margin-bottom: 16px">
      <el-form :inline="true">
        <el-form-item label="设备ID"><el-input v-model="deviceId" placeholder="必填" style="width: 200px" /></el-form-item>
        <el-form-item label="作者ID"><el-input v-model="authorId" placeholder="可选" style="width: 180px" /></el-form-item>
        <el-form-item label="风险等级">
          <el-select v-model="riskLevel" placeholder="全部" clearable style="width: 130px">
            <el-option label="高风险" value="HIGH" /><el-option label="中风险" value="MEDIUM" /><el-option label="安全" value="SAFE" />
          </el-select>
        </el-form-item>
        <el-form-item label="开始日期"><el-date-picker v-model="startDate" type="date" value-format="YYYY-MM-DD" /></el-form-item>
        <el-form-item label="结束日期"><el-date-picker v-model="endDate" type="date" value-format="YYYY-MM-DD" /></el-form-item>
        <el-form-item><el-button type="primary" @click="load" :loading="loading">查询</el-button></el-form-item>
      </el-form>
    </el-card>

    <el-card>
      <el-table :data="records" v-loading="loading" stripe>
        <el-table-column prop="id" label="ID" width="80" />
        <el-table-column prop="deviceId" label="设备ID" width="180" />
        <el-table-column prop="authorId" label="作者ID" width="180" />
        <el-table-column prop="riskLevel" label="风险等级" width="100">
          <template #default="{ row }"><RiskBadge :level="row.riskLevel" /></template>
        </el-table-column>
        <el-table-column prop="score" label="评分" width="100" :formatter="(r: any) => Number(r.score).toFixed(5)" />
        <el-table-column prop="createdAt" label="检测时间" :formatter="(r: any) => formatDate(r.createdAt)" />
      </el-table>
      <div style="margin-top: 16px; text-align: right">
        <el-pagination
          v-model:current-page="page" v-model:page-size="size" :total="total"
          :page-sizes="[10, 20, 50, 100]" layout="total, sizes, prev, pager, next"
          @current-change="onPageChange" @size-change="onSizeChange"
        />
      </div>
    </el-card>
  </div>
</template>
