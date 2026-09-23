const API_BASE_URL = import.meta.env.VITE_API_BASE_URL ?? 'http://localhost:8080/api/v1'

async function request<T>(path: string, init?: RequestInit): Promise<T> {
  const response = await fetch(`${API_BASE_URL}${path}`, {
    ...init,
    headers: { 'Content-Type': 'application/json', ...(getAuthToken() ? { Authorization: `Bearer ${getAuthToken()}` } : {}), ...(init?.headers ?? {}) },
  })
  if (!response.ok) {
    const body = await response.text()
    throw new Error(body || `${response.status} ${response.statusText}`)
  }
  return response.json() as Promise<T>
}

export async function apiRequest<T>(path: string, init?: RequestInit): Promise<T> { return request<T>(path, init) }
export function getAuthToken(): string | null { return localStorage.getItem('hedera.auth.token') }
export function setAuthToken(token: string, user?: unknown): void { localStorage.setItem('hedera.auth.token', token); if (user) localStorage.setItem('hedera.auth.user', JSON.stringify(user)) }
export function clearAuthToken(): void { localStorage.removeItem('hedera.auth.token'); localStorage.removeItem('hedera.auth.user') }
export function getAuthUser<T = { role: string; displayName: string; email: string }>(): T | null { const value = localStorage.getItem('hedera.auth.user'); return value ? JSON.parse(value) as T : null }
export async function updateProfile(email: string, displayName: string) { const result = await request<any>('/auth/me/profile', { method: 'PUT', body: JSON.stringify({ email, displayName }) }); setAuthToken(result.token, result); return result }
export async function changePassword(currentPassword: string, newPassword: string) { return request<void>('/auth/me/password', { method: 'PUT', body: JSON.stringify({ currentPassword, newPassword }) }) }
export async function logout() { try { await request<void>('/auth/logout', { method: 'POST' }) } finally { clearAuthToken() } }
export async function deleteAccount() { try { await request<void>('/auth/me', { method: 'DELETE' }) } finally { clearAuthToken() } }
export async function getCurrentAccount() { return request<{ id: string; hederaAccountId: string; balance: string; status: string }>('/accounts/me') }

export async function getHealth(): Promise<{ status: string }> {
  return request('/health')
}

/** Whether audit events are really being written to Hedera. */
export async function getAuditStatus(): Promise<{ ledgerActive: boolean }> {
  return request('/audit/status')
}

export async function listAuditEvents(): Promise<AuditEvent[]> {
  return request('/audit')
}

export async function recordAuditEvent(body: RecordAuditEventRequest): Promise<AuditEvent> {
  return request('/audit', { method: 'POST', body: JSON.stringify(body) })
}

/** Re-reads the event from the Mirror Node and compares hashes. */
export async function verifyAuditEvent(id: string): Promise<VerificationResult> {
  return request(`/audit/${encodeURIComponent(id)}/verification`)
}

export type AnchorStatus = 'PENDING' | 'ANCHORED' | 'FAILED'

export type ActorType = 'USER' | 'AGENT' | 'SYSTEM'

export type AuditEvent = {
  id: string
  agent: string
  action: string
  status: string
  createdAt: string | null
  /** Who the action is attributed to. Resolved server-side, never sent by the client. */
  actorType: ActorType | null
  actorId: string | null
  actorHederaAccountId: string | null
  anchorStatus: AnchorStatus
  topicId: string | null
  transactionId: string | null
  consensusTimestamp: string | null
  sequenceNumber: number | null
  payloadHash: string | null
}

export type RecordAuditEventRequest = {
  agent: string
  action: string
  status: string
  metadata?: Record<string, string>
}

export type VerificationResult = {
  verified: boolean
  detail: string
  /** SHA-256 of what our database holds. */
  storedHash: string | null
  /** SHA-256 of what the ledger actually holds. Differs from storedHash when the record was altered. */
  ledgerHash: string | null
  storedPayload: string | null
  ledgerPayload: string | null
  consensusTimestamp: string | null
  explorerUrl: string | null
}
