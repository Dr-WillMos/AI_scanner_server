<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { listBlacklist, addToBlacklist, removeFromBlacklist, type BlacklistType } from '../api/blacklist'
import { ElMessage, ElMessageBox } from 'element-plus'

const activeTab = ref<BlacklistType>('authority')
const items = ref<string[]>([])
const loading = ref(false)
const newId = ref('')
const newReason = ref('')
const batchIds = ref('')

const tabLabel: Record<BlacklistType, string> = { authority: '权威黑名单', global: '全局黑名单', temp: '临时黑名单' }

async function load() {
  loading.value = true
  try { items.value = await listBlacklist(activeTab.value) } finally { loading.value = false }
}

async function add() {
  if (!newId.value.trim()) return
  await addToBlacklist(activeTab.value, newId.value.trim(), activeTab.value === 'temp' ? newReason.value || undefined : undefined)
  ElMessage.success('已添加')
  newId.value = ''; newReason.value = ''
  load()
}

async function remove(id: string) {
  await ElMessageBox.confirm(`确认从 ${tabLabel[activeTab.value]} 中移除 "${id}"？`, '确认移除', { type: 'warning' })
  await removeFromBlacklist(activeTab.value, id)
  ElMessage.success('已移除')
  load()
}

async function batchAdd() {
  const ids = batchIds.value.split(/[\n,，]+/).map(s => s.trim()).filter(Boolean)
  if (ids.length === 0) return
  await ElMessageBox.confirm(`确认批量添加 ${ids.length} 个 ID 到 ${tabLabel[activeTab.value]}？`, '确认添加', { type: 'warning' })
  for (const id of ids) {
    await addToBlacklist(activeTab.value, id, activeTab.value === 'temp' ? '批量导入' : undefined)
  }
  batchIds.value = ''
  ElMessage.success(`已添加 ${ids.length} 条`)
  load()
}

onMounted(load)
</script>

<template>
  <el-card>
    <el-tabs v-model="activeTab" @tab-change="load">
      <el-tab-pane label="权威黑名单" name="authority" />
      <el-tab-pane label="全局黑名单" name="global" />
      <el-tab-pane label="临时黑名单" name="temp" />
    </el-tabs>

    <el-form :inline="true" style="margin-top: 16px">
      <el-form-item label="添加 ID">
        <el-input v-model="newId" placeholder="输入 authorId" style="width: 200px" />
      </el-form-item>
      <el-form-item v-if="activeTab === 'temp'" label="原因">
        <el-input v-model="newReason" placeholder="可选" style="width: 160px" />
      </el-form-item>
      <el-form-item><el-button type="primary" @click="add">添加</el-button></el-form-item>
    </el-form>

    <el-form style="margin-bottom: 16px">
      <el-form-item label="批量导入">
        <el-input v-model="batchIds" type="textarea" :rows="3" placeholder="每行一个 authorId，或用逗号分隔" />
      </el-form-item>
      <el-form-item><el-button @click="batchAdd">批量导入</el-button></el-form-item>
    </el-form>

    <el-table :data="items.map(id => ({ authorId: id }))" v-loading="loading" stripe max-height="500">
      <el-table-column prop="authorId" label="Author ID" />
      <el-table-column label="操作" width="100">
        <template #default="{ row }">
          <el-button type="danger" size="small" @click="remove(row.authorId)">移除</el-button>
        </template>
      </el-table-column>
    </el-table>
  </el-card>
</template>
