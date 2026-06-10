import client from './client'

export interface DlqMessage {
  messageId: string
  taskId: string
  deviceId: string
  authorId: string
  filePath: string
  error: string
  retryCount: number
  dlqRetryCount: number
  enteredAt: string
}

export interface DlqStats {
  totalPending: number
  finalDeadLetters: number
  oldestMessageTime: string
  alertThreshold: number
  alertActive: boolean
}

export async function fetchDlqMessages(count = 50): Promise<DlqMessage[]> {
  const res = await client.get('/api/v1/dlq', { params: { count } })
  return res.data.data
}

export async function fetchDlqMessage(messageId: string): Promise<DlqMessage> {
  const res = await client.get(`/api/v1/dlq/${messageId}`)
  return res.data.data
}

export async function retryDlqMessage(messageId: string): Promise<DlqMessage> {
  const res = await client.post(`/api/v1/dlq/${messageId}/retry`)
  return res.data.data
}

export async function deleteDlqMessage(messageId: string): Promise<void> {
  await client.delete(`/api/v1/dlq/${messageId}`)
}

export async function fetchDlqStats(): Promise<DlqStats> {
  const res = await client.get('/api/v1/dlq/stats')
  return res.data.data
}

export async function purgeDlq(): Promise<void> {
  await client.delete('/api/v1/dlq/purge')
}
