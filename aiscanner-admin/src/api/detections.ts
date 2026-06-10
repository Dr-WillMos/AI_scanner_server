import client from './client'

export interface HistoryItem {
  id: number
  deviceId: string
  authorId: string
  riskLevel: string
  score: number
  createdAt: string
}

export interface HistoryResponse {
  records: HistoryItem[]
  total: number
  page: number
  size: number
  hasMore: boolean
  latestId: number | null
}

export async function fetchDetections(params: {
  deviceId: string
  authorId?: string
  riskLevel?: string
  startDate?: string
  endDate?: string
  page?: number
  size?: number
}): Promise<HistoryResponse> {
  const res = await client.get('/api/v1/history', { params })
  return res.data.data
}
