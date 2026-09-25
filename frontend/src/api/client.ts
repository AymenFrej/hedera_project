const API_BASE_URL = import.meta.env.VITE_API_BASE_URL ?? 'http://localhost:8080/api/v1'

async function request<T>(path: string, init?: RequestInit): Promise<T> {
  const response = await fetch(`${API_BASE_URL}${path}`, {
    ...init,
    headers: { 'Content-Type': 'application/json', ...(getAuthToken() ? { Authorization: `Bearer ${getAuthToken()}` } : {}), ...(init?.headers ?? {}) },
  })
  if (!response.ok) {
    const body = await response.text()
    if (response.status === 401 && !['/auth/login', '/auth/register'].includes(path)) {
      clearAuthToken()
      window.location.assign('/login')
    }
    let message = body
    try { const parsed = JSON.parse(body); message = parsed.detail || parsed.message || parsed.error || body } catch { /* plain error */ }
    throw new Error(message || `${response.status} ${response.statusText}`)
  }
  const body = await response.text()
  return (body ? JSON.parse(body) : undefined) as T
}

export async function apiRequest<T>(path: string, init?: RequestInit): Promise<T> { return request<T>(path, init) }
export type AssistantChatSource = { title: string; url: string }
export type AssistantChatResponse = { message: string; sources?: AssistantChatSource[] }
export async function sendAssistantMessage(message: string): Promise<AssistantChatResponse> {
  return request<AssistantChatResponse>('/assistant/chat', { method: 'POST', body: JSON.stringify({ message }) })
}
export function getAuthToken(): string | null { return localStorage.getItem('hedera.auth.token') }
export function setAuthToken(token: string, user?: unknown): void { localStorage.setItem('hedera.auth.token', token); if (user) localStorage.setItem('hedera.auth.user', JSON.stringify(user)) }
export function clearAuthToken(): void { localStorage.removeItem('hedera.auth.token'); localStorage.removeItem('hedera.auth.user') }
export function getAuthUser<T = { role: string; displayName: string; email: string }>(): T | null { const value = localStorage.getItem('hedera.auth.user'); try { return value ? JSON.parse(value) as T : null } catch { return null } }
export async function updateProfile(email: string, displayName: string) { const result = await request<any>('/auth/me/profile', { method: 'PUT', body: JSON.stringify({ email, displayName }) }); setAuthToken(result.token, result); return result }
export async function changePassword(currentPassword: string, newPassword: string) { return request<void>('/auth/me/password', { method: 'PUT', body: JSON.stringify({ currentPassword, newPassword }) }) }
export async function logout() {
  const token = getAuthToken()
  clearAuthToken()
  try { await fetch(`${API_BASE_URL}/auth/logout`, {method: 'POST', headers: token ? {Authorization: `Bearer ${token}`} : {}, signal: AbortSignal.timeout(5000)}) } catch { /* Local logout must work offline. */ }
}
export async function deleteAccount() { await request<void>('/auth/me', { method: 'DELETE' }); clearAuthToken() }
export async function getCurrentAccount() { return request<{ id: string; hederaAccountId: string; balance: string; status: string }>('/accounts/me') }
export type ManagedUser = { id: string; email: string; displayName: string; role: string; accountId: string; hederaAccountId: string }
export async function listManagedUsers() { return request<ManagedUser[]>('/admin/users') }
export async function createManagedUser(body: { email: string; password: string; displayName: string; role: string }) { return request<ManagedUser>('/admin/users', { method: 'POST', body: JSON.stringify(body) }) }
export async function updateManagedUserRole(id: string, role: string) { return request<ManagedUser>(`/admin/users/${id}/role`, { method: 'PUT', body: JSON.stringify({ role }) }) }
export async function deleteManagedUser(id: string) { return request<void>(`/admin/users/${id}`, { method: 'DELETE' }) }
export async function restoreManagedUser(id: string, role: string) { return request<ManagedUser>(`/admin/users/${id}/restore`, { method: 'POST', body: JSON.stringify({role}) }) }
export async function provisionManagedWallet(id: string) { return request<ManagedUser>(`/admin/users/${id}/wallet`, { method: 'POST' }) }
export async function deleteMockUser(id: string) { return request<void>(`/admin/users/${id}/mock`, { method: 'DELETE' }) }
export async function editManagedProfile(id: string, email: string, displayName: string) { return request<ManagedUser>(`/admin/users/${id}/profile`, {method: 'PUT', body: JSON.stringify({email, displayName})}) }

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

/** The demo budget the policy engine decides against. */
export async function getPolicyState(): Promise<PolicyStateResponse> {
  return request('/policies/state')
}

/** Puts the demo back to its opening balances. The audit trail is never cleared. */
export async function resetDemo(): Promise<PolicyStateResponse> {
  return request('/policies/demo/reset', { method: 'POST' })
}

/** Submits a spend to the deterministic rule engine. Never decides client-side. */
export async function decideSpend(body: DecideRequest): Promise<DecideResponse> {
  return request('/policies/decide', { method: 'POST', body: JSON.stringify(body) })
}

/** The queue of HOLD decisions waiting for a human, newest first. */
export async function listApprovals(): Promise<ApprovalRequest[]> {
  return request('/policies/approvals')
}

/** Records a human's answer. 409 if the request was already settled. */
export async function answerApproval(
  id: string,
  answer: 'approve' | 'reject',
): Promise<ApprovalRequest> {
  return request(`/policies/approvals/${encodeURIComponent(id)}/${answer}`, { method: 'POST' })
}

export type ApprovalRequest = {
  id: string
  /** Audit event id of the decision that produced this request. */
  taskId: string | null
  status: 'PENDING' | 'APPROVED' | 'REJECTED'
  ruleId: string
  reason: string
  envelope: string | null
  amount: number | null
  counterparty: string | null
  requestedAt: string | null
  decidedAt: string | null
  decidedBy: string | null
}

export type PolicyStateResponse = {
  envelopes: Record<string, number>
  knownCounterparties: string[]
}

export type DecideRequest = {
  envelope: string
  amount: number
  counterparty: string
}

export type Verdict = 'ALLOW' | 'HOLD' | 'DENY'

export type DecideResponse = {
  verdict: Verdict
  /** Stable rule identifier, e.g. "amount.large". Survives rewording of the reason. */
  ruleId: string
  reason: string
  balanceAfter: number | null
  /** Present only for HOLD: the handle a human uses to settle the request. */
  approvalId: string | null
  /** The audit event this decision wrote. Open it on /audit to see the proof. */
  auditEventId: string | null
  /** False when the decision never reached the ledger — shown rather than implied. */
  anchored: boolean
  /** The envelopes as they stand after this decision, so the screen cannot go stale. */
  envelopes: Record<string, number>
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

export type PolicyRule = { ruleId: string; verdict: string; reason: string }
export async function getPolicyRules(): Promise<PolicyRule[]> { return request('/policies') }
export async function getAuditEvent(id: string): Promise<AuditEvent> { return request(`/audit/${encodeURIComponent(id)}`) }
