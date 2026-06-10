import client from './client'

export interface DashboardStats {
  totalDetections: number
  byRiskLevel: Record<string, number>
  blacklistHits: number
  aiAvgDurationMs: number
  aiCallCount: number
  blacklistCounts: { authority: number; global: number; temp: number }
}

export async function fetchStats(): Promise<DashboardStats> {
  const res = await client.get('/api/v1/stats')
  return res.data.data
}
