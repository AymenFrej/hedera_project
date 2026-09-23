const API_BASE_URL = import.meta.env.VITE_API_BASE_URL ?? 'http://localhost:8080/api/v1'

async function request<T>(path: string, init?: RequestInit): Promise<T> {
  const response = await fetch(`${API_BASE_URL}${path}`, {
    ...init,
    headers: { 'Content-Type': 'application/json', ...(init?.headers ?? {}) },
  })
  if (!response.ok) {
    const body = await response.text()
    throw new Error(body || `${response.status} ${response.statusText}`)
  }
  return response.json() as Promise<T>
}

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

/** Submits a spend to the deterministic rule engine. Never decides client-side. */
export async function decideSpend(body: DecideRequest): Promise<DecideResponse> {
  return request('/policies/decide', { method: 'POST', body: JSON.stringify(body) })
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
