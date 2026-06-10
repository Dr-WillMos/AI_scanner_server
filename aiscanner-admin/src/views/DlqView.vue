<script setup lang="ts">
import { ref, onMounted, onUnmounted } from 'vue'
import {
  fetchDlqMessages,
  fetchDlqStats,
  retryDlqMessage,
  deleteDlqMessage,
  purgeDlq,
  type DlqMessage,
  type DlqStats,
} from '../api/dlq'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Warning } from '@element-plus/icons-vue'

const messages = ref<DlqMessage[]>([])
const stats = ref<DlqStats>({ totalPending: 0, finalDeadLetters: 0, oldestMessageTime: '', alertThreshold: 10, alertActive: false })
const loading = ref(false)
const detailVisible = ref(false)
const detailMsg = ref<DlqMessage | null>(null)

let timer: ReturnType<typeof setInterval> | null = null

async function load() {
  loading.value = true
  try {
    const [msgs, st] = await Promise.all([fetchDlqMessages(), fetchDlqStats()])
    messages.value = msgs
    stats.value = st
  } catch {
    // error handled by interceptor
  } finally {
    loading.value = false
  }
}

async function retry(messageId: string) {
  await ElMessageBox.confirm('确认重新投递该消息到主队列？', '确认重试', { type: 'info' })
  await retryDlqMessage(messageId)
  ElMessage.success('已重新投递')
  load()
}

async function remove(messageId: string) {
  await ElMessageBox.confirm('确认删除该死信消息？此操作不可恢复。', '确认删除', { type: 'warning' })
  await deleteDlqMessage(messageId)
  ElMessage.success('已删除')
  load()
}

async function purge() {
  await ElMessageBox.confirm('确认清空全部死信消息？此操作不可恢复。', '确认清空', { type: 'warning', confirmButtonClass: 'el-button--danger' })
  await purgeDlq()
  ElMessage.success('已清空死信队列')
  load()
}

function showDetail(msg: DlqMessage) {
  detailMsg.value = msg
  detailVisible.value = true
}

onMounted(() => {
  load()
  timer = setInterval(load, 30_000)
})

onUnmounted(() => {
  if (timer) clearInterval(timer)
})
</script>

<template>
  <div>
    <el-row :gutter="16" style="margin-bottom: 16px">
      <el-col :span="6">
        <el-card shadow="hover">
          <template #header>待处理总数</template>
          <div style="font-size: 28px; font-weight: 700">{{ stats.totalPending }}</div>
        </el-card>
      </el-col>
      <el-col :span="6">
        <el-card shadow="hover">
          <template #header>最终死信</template>
          <div style="font-size: 28px; font-weight: 700; color: #e6a23c">{{ stats.finalDeadLetters }}</div>
        </el-card>
      </el-col>
      <el-col :span="6">
        <el-card shadow="hover">
          <template #header>最旧消息时间</template>
          <div style="font-size: 14px; color: #606266">{{ stats.oldestMessageTime || 'N/A' }}</div>
        </el-card>
      </el-col>
      <el-col :span="6">
        <el-card shadow="hover">
          <template #header>告警状态</template>
          <el-tag :type="stats.alertActive ? 'danger' : 'success'" size="large">
            {{ stats.alertActive ? '积压告警' : '正常' }}
          </el-tag>
        </el-card>
      </el-col>
    </el-row>

    <div style="margin-bottom: 12px; display: flex; justify-content: space-between; align-items: center">
      <span style="color: #606266">共 {{ messages.length }} 条记录</span>
      <div style="display: flex; gap: 8px">
        <el-button @click="load" :loading="loading">刷新</el-button>
        <el-button type="danger" @click="purge" :disabled="messages.length === 0">清空全部</el-button>
      </div>
    </div>

    <el-table :data="messages" v-loading="loading" stripe style="width: 100%">
      <el-table-column prop="messageId" label="消息 ID" min-width="160">
        <template #default="{ row }">
          <span style="font-family: monospace; font-size: 12px">{{ row.messageId?.slice(0, 20) }}...</span>
        </template>
      </el-table-column>
      <el-table-column prop="taskId" label="任务 ID" min-width="140">
        <template #default="{ row }">
          <span style="font-family: monospace; font-size: 12px">{{ row.taskId?.slice(0, 12) }}...</span>
        </template>
      </el-table-column>
      <el-table-column prop="authorId" label="作者 ID" width="120" />
      <el-table-column prop="error" label="错误信息" min-width="200">
        <template #default="{ row }">
          <el-tooltip :content="row.error" placement="top" :show-after="300">
            <span style="color: #f56c6c; cursor: default">{{ row.error?.slice(0, 40) }}{{ row.error?.length > 40 ? '...' : '' }}</span>
          </el-tooltip>
        </template>
      </el-table-column>
      <el-table-column prop="retryCount" label="主队列重试" width="100" align="center" />
      <el-table-column label="DLQ重试" width="100" align="center">
        <template #default="{ row }">
          <el-tag :type="row.dlqRetryCount >= 4 ? 'danger' : 'warning'" size="small">{{ row.dlqRetryCount }}</el-tag>
        </template>
      </el-table-column>
      <el-table-column prop="enteredAt" label="进入时间" width="170" />
      <el-table-column label="操作" width="180" fixed="right">
        <template #default="{ row }">
          <el-button link type="primary" @click="showDetail(row)">详情</el-button>
          <el-button link type="warning" @click="retry(row.messageId)">重试</el-button>
          <el-button link type="danger" @click="remove(row.messageId)">删除</el-button>
        </template>
      </el-table-column>
    </el-table>

    <el-dialog v-model="detailVisible" title="死信消息详情" width="640px">
      <template v-if="detailMsg">
        <el-descriptions :column="2" border>
          <el-descriptions-item label="消息 ID" :span="2">{{ detailMsg.messageId }}</el-descriptions-item>
          <el-descriptions-item label="任务 ID">{{ detailMsg.taskId }}</el-descriptions-item>
          <el-descriptions-item label="设备 ID">{{ detailMsg.deviceId }}</el-descriptions-item>
          <el-descriptions-item label="作者 ID">{{ detailMsg.authorId }}</el-descriptions-item>
          <el-descriptions-item label="文件路径">{{ detailMsg.filePath }}</el-descriptions-item>
          <el-descriptions-item label="主队列重试次数">{{ detailMsg.retryCount }}</el-descriptions-item>
          <el-descriptions-item label="DLQ重试次数">
            <el-tag :type="detailMsg.dlqRetryCount >= 4 ? 'danger' : 'warning'" size="small">{{ detailMsg.dlqRetryCount }}</el-tag>
          </el-descriptions-item>
          <el-descriptions-item label="进入时间">{{ detailMsg.enteredAt }}</el-descriptions-item>
          <el-descriptions-item label="错误信息" :span="2">
            <span style="color: #f56c6c">{{ detailMsg.error }}</span>
          </el-descriptions-item>
        </el-descriptions>
      </template>
      <template #footer>
        <el-button @click="detailVisible = false">关闭</el-button>
      </template>
    </el-dialog>
  </div>
</template>
