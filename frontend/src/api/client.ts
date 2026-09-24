const API_BASE_URL = import.meta.env.VITE_API_BASE_URL ?? 'http://localhost:8080/api/v1'

async function request<T>(path: string, init?: RequestInit): Promise<T> {
  const response = await fetch(`${API_BASE_URL}${path}`, {
    ...init,
    headers: { 'Content-Type': 'application/json', ...(init?.headers ?? {}) },
  })
  if (!response.ok) {
    const body = await response.text()
    // A refused action answers with {"error": "..."} — show that sentence, not raw JSON.
    let message = body
    try {
      message = (JSON.parse(body).error as string) ?? body
    } catch {
      /* not JSON: keep the raw body */
    }
    throw new Error(message || `${response.status} ${response.statusText}`)
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
