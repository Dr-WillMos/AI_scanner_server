import client from './client'

export type BlacklistType = 'authority' | 'global' | 'temp'

export async function listBlacklist(type: BlacklistType): Promise<string[]> {
  const res = await client.get(`/api/v1/blacklist/${type}`)
  return res.data.data
}

export async function addToBlacklist(type: BlacklistType, authorId: string, reason?: string) {
  const body: Record<string, string> = { authorId }
  if (reason) body.reason = reason
  await client.post(`/api/v1/blacklist/${type}`, body)
}

export async function removeFromBlacklist(type: BlacklistType, authorId: string) {
  await client.delete(`/api/v1/blacklist/${type}/${authorId}`)
}

export async function checkBlacklist(type: BlacklistType, authorId: string) {
  const res = await client.get(`/api/v1/blacklist/${type}/${authorId}`)
  return res.data.data
}
