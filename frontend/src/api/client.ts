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

/** Whether payments really reach Hedera, or are only simulated. */
export async function getPaymentStatus(): Promise<{ ledgerActive: boolean }> {
  return request('/payments/status')
}

export async function listPayments(): Promise<Payment[]> {
  return request('/payments')
}

/** Checks the policy, then sends the transfer or holds it for approval. */
export async function createPayment(body: CreatePaymentRequest): Promise<Payment> {
  return request('/payments', { method: 'POST', body: JSON.stringify(body) })
}

export async function approvePayment(id: string): Promise<Payment> {
  return request(`/payments/${encodeURIComponent(id)}/approve`, { method: 'POST' })
}

export async function rejectPayment(id: string): Promise<Payment> {
  return request(`/payments/${encodeURIComponent(id)}/reject`, { method: 'POST' })
}

export type PaymentStatus =
  | 'PENDING'
  | 'AWAITING_APPROVAL'
  | 'REJECTED'
  | 'SUBMITTED'
  | 'CONFIRMED'
  | 'FAILED'
  | 'SIMULATED'

export type Payment = {
  id: string
  amount: string
  /** "HBAR", or the HTS token id. */
  currency: string
  tokenId: string | null
  destination: string
  envelope: string | null
  memo: string | null
  status: PaymentStatus
  sourceAccount: string | null
  transactionId: string | null
  explorerUrl: string | null
  policyVerdict: 'ALLOW' | 'HOLD' | 'DENY' | null
  policyRuleId: string | null
  policyReason: string | null
  failureReason: string | null
  /** Who asked for the payment. Resolved server-side, never sent by the client. */
  requestedByType: 'USER' | 'AGENT' | 'SYSTEM' | null
  requestedById: string | null
  createdAt: string | null
  updatedAt: string | null
}

export type CreatePaymentRequest = {
  destination: string
  amount: string
  tokenId?: string
  envelope?: string
  memo?: string
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
