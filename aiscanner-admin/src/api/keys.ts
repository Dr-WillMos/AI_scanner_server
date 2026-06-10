import client from './client'

export interface KeyInfo {
  id: number
  keyName: string
  deviceId: string
  permissions: string
  status: string
  rateLimit: number
  lastUsedAt: string | null
  expiredAt: string | null
  createdAt: string
  keyValue?: string
  revokedAt?: string | null
}

export async function listKeys(): Promise<KeyInfo[]> {
  const res = await client.get('/api/v1/keys')
  return res.data.data
}

export async function getKeyDetail(id: number): Promise<KeyInfo> {
  const res = await client.get(`/api/v1/keys/${id}`)
  return res.data.data
}

export async function createKey(body: {
  keyName: string
  permissions: string
  rateLimit: number
  expiredAt?: string
}): Promise<{ apiKey: string }> {
  const res = await client.post('/api/v1/keys', body)
  return res.data.data
}

export async function updateKey(id: number, body: {
  keyName?: string
  permissions?: string
  rateLimit?: number
  expiredAt?: string | null
}) {
  await client.put(`/api/v1/keys/${id}`, body)
}

export async function revokeKey(id: number) {
  await client.post(`/api/v1/keys/${id}/revoke`)
}

export async function deleteKey(id: number) {
  await client.delete(`/api/v1/keys/${id}`)
}
