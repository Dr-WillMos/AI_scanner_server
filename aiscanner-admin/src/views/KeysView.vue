<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { listKeys, getKeyDetail, createKey, updateKey, revokeKey, deleteKey, type KeyInfo } from '../api/keys'
import { ElMessage, ElMessageBox } from 'element-plus'

const keys = ref<KeyInfo[]>([])
const loading = ref(false)
const showCreate = ref(false)
const showDetail = ref(false)
const detailKey = ref<KeyInfo | null>(null)

const form = ref({ keyName: '', permissions: 'DETECT,HISTORY', rateLimit: 20, expiredAt: '' })

async function load() {
  loading.value = true
  try { keys.value = await listKeys() } finally { loading.value = false }
}

async function onCreate() {
  await createKey({
    keyName: form.value.keyName,
    permissions: form.value.permissions,
    rateLimit: form.value.rateLimit,
    expiredAt: form.value.expiredAt || undefined,
  })
  ElMessage.success('Key 已创建')
  showCreate.value = false
  form.value = { keyName: '', permissions: 'DETECT,HISTORY', rateLimit: 20, expiredAt: '' }
  load()
}

async function onUpdate(id: number) {
  const { value: form } = await ElMessageBox.prompt('输入新的权限（逗号分隔）', '编辑权限', {
    confirmButtonText: '保存', inputValue: 'DETECT,HISTORY'
  })
  if (!form) return
  await updateKey(id, { permissions: form })
  ElMessage.success('已更新')
  load()
}

async function onRevoke(id: number) {
  await ElMessageBox.confirm('确认吊销此 Key？吊销后该 Key 将无法使用。', '确认吊销', { type: 'warning' })
  await revokeKey(id)
  ElMessage.success('已吊销')
  load()
}

async function onDelete(id: number) {
  await ElMessageBox.confirm('确认删除此 Key？此操作不可恢复。', '确认删除', { type: 'warning' })
  await deleteKey(id)
  ElMessage.success('已删除')
  load()
}

async function showKeyDetail(id: number) {
  detailKey.value = await getKeyDetail(id)
  showDetail.value = true
}

function formatDate(d: string | null) {
  return d ? d.replace('T', ' ') : '-'
}

onMounted(load)
</script>

<template>
  <el-card>
    <div style="margin-bottom: 16px">
      <el-button type="primary" @click="showCreate = true">创建 Key</el-button>
    </div>

    <el-table :data="keys" v-loading="loading" stripe>
      <el-table-column prop="id" label="ID" width="60" />
      <el-table-column prop="keyName" label="名称" width="150" />
      <el-table-column prop="deviceId" label="设备ID" width="150" />
      <el-table-column prop="permissions" label="权限" width="180" />
      <el-table-column prop="status" label="状态" width="80">
        <template #default="{ row }">
          <el-tag :type="row.status === 'ACTIVE' ? 'success' : 'danger'" size="small">{{ row.status }}</el-tag>
        </template>
      </el-table-column>
      <el-table-column prop="rateLimit" label="限流(次/分)" width="100" />
      <el-table-column prop="lastUsedAt" label="最后使用" :formatter="(r: any) => formatDate(r.lastUsedAt)" />
      <el-table-column prop="expiredAt" label="过期时间" :formatter="(r: any) => formatDate(r.expiredAt)" />
      <el-table-column label="操作" width="260" fixed="right">
        <template #default="{ row }">
          <el-button size="small" @click="showKeyDetail(row.id)">详情</el-button>
          <el-button size="small" @click="onUpdate(row.id)">编辑</el-button>
          <el-button v-if="row.status === 'ACTIVE'" size="small" type="warning" @click="onRevoke(row.id)">吊销</el-button>
          <el-button size="small" type="danger" @click="onDelete(row.id)">删除</el-button>
        </template>
      </el-table-column>
    </el-table>

    <el-dialog v-model="showCreate" title="创建 API Key" width="500px">
      <el-form>
        <el-form-item label="名称"><el-input v-model="form.keyName" placeholder="描述用途" /></el-form-item>
        <el-form-item label="权限"><el-input v-model="form.permissions" placeholder="DETECT,HISTORY,ADMIN" /></el-form-item>
        <el-form-item label="限流(次/分钟)"><el-input-number v-model="form.rateLimit" :min="1" :max="1000" /></el-form-item>
        <el-form-item label="过期时间"><el-date-picker v-model="form.expiredAt" type="datetime" value-format="YYYY-MM-DDTHH:mm:ss" placeholder="不填则永不过期" /></el-form-item>
      </el-form>
      <template #footer><el-button @click="showCreate = false">取消</el-button><el-button type="primary" @click="onCreate">创建</el-button></template>
    </el-dialog>

    <el-dialog v-model="showDetail" title="Key 详情" width="500px">
      <template v-if="detailKey">
        <el-descriptions :column="1" border>
          <el-descriptions-item label="Key 值"><el-input :value="detailKey.keyValue" readonly /></el-descriptions-item>
          <el-descriptions-item label="名称">{{ detailKey.keyName }}</el-descriptions-item>
          <el-descriptions-item label="权限">{{ detailKey.permissions }}</el-descriptions-item>
          <el-descriptions-item label="创建时间">{{ formatDate(detailKey.createdAt) }}</el-descriptions-item>
        </el-descriptions>
      </template>
      <template #footer><el-button @click="showDetail = false">关闭</el-button></template>
    </el-dialog>
  </el-card>
</template>
