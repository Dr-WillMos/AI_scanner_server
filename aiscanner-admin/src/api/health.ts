import axios from 'axios'
import { useAuthStore } from '../stores/auth'

export interface HealthStatus {
  status: string
  components?: Record<string, { status: string; details?: any }>
}

export async function fetchHealth(): Promise<HealthStatus> {
  // Don't use the main client — health returns 503 when components are DOWN,
  // and axios by default rejects non-2xx, preventing us from reading the body.
  const auth = useAuthStore()
  const res = await axios.get('/actuator/health', {
    headers: auth.apiKey ? { 'X-API-Key': auth.apiKey } : {},
    validateStatus: () => true, // accept any status code
  })
  return res.data
}
