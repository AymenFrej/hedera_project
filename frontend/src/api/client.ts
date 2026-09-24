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

export type PaymentsStatus = {
  /** Whether payments really reach Hedera, or are only simulated. */
  ledgerActive: boolean
  /** Demo HTS token and a recipient already associated with it, when configured. */
  demoTokenId: string | null
  demoRecipientId: string | null
}

export async function getPaymentStatus(): Promise<PaymentsStatus> {
  return request('/payments/status')
}

/** Balances of the paying account, from the Mirror Node. Facts only. */
export async function getPaymentBalance(): Promise<Balance> {
  return request('/payments/balance')
}

export type BalanceAsset = {
  tokenId: string | null
  symbol: string | null
  name: string | null
  decimals: number
  /** Smallest unit: tinybars, or the token's smallest unit. */
  units: number
  /** Same balance as a decimal string. */
  amount: string
}

export type Balance = {
  available: boolean
  detail: string | null
  account: string | null
  hbar: BalanceAsset | null
  tokens: BalanceAsset[]
  asOf: string | null
  explorerUrl: string | null
}

export async function listPayments(): Promise<Payment[]> {
  return request('/payments')
}

/**
 * Checks the policy, then sends the transfer or holds it for approval.
 *
 * `idempotencyKey` identifies one payment attempt: sending it again (double click, retry after a
 * network error) returns the payment already created instead of paying twice.
 */
export async function createPayment(
  body: CreatePaymentRequest,
  idempotencyKey: string,
): Promise<Payment> {
  return request('/payments', {
    method: 'POST',
    body: JSON.stringify(body),
    headers: { 'Idempotency-Key': idempotencyKey },
  })
}

/**
 * What executing this payment would do: the policy verdict as returned, and ledger facts.
 * Nothing is recorded or sent, and it authorizes nothing: executing asks the policy again.
 */
export async function previewPayment(body: CreatePaymentRequest): Promise<PaymentPreview> {
  return request('/payments/preview', { method: 'POST', body: JSON.stringify(body) })
}

export type PreviewOutcome = 'READY' | 'NEEDS_APPROVAL' | 'BLOCKED' | 'LIKELY_TO_FAIL' | 'SIMULATION'

export type PaymentPreview = {
  outcome: PreviewOutcome
  summary: string
  payerAccount: string | null
  destination: string
  amount: string
  amountUnits: number
  tokenId: string | null
  symbol: string | null
  balanceBefore: string | null
  balanceAfter: string | null
  policy: {
    verdict: 'ALLOW' | 'HOLD' | 'DENY'
    ruleId: string | null
    reason: string | null
    /** What the envelope holds now; null without an envelope. */
    available: string | null
    /** How much more than `available` is asked; null when it fits. */
    shortfall: string | null
  }
  checks: { name: string; status: 'PASS' | 'WARN' | 'FAIL' | 'UNKNOWN'; detail: string }[]
  note: string
}

/** Everything the result screen shows, each part from something that actually happened. */
export async function getPaymentResult(id: string): Promise<PaymentReceipt> {
  return request(`/payments/${encodeURIComponent(id)}/result`)
}

/** Reads one of the payment's audit events back from HCS. */
export async function verifyPaymentAuditEvent(
  paymentId: string,
  eventId: string,
): Promise<VerificationResult> {
  return request(
    `/payments/${encodeURIComponent(paymentId)}/audit/${encodeURIComponent(eventId)}/verification`,
  )
}

export type ReceiptOutcome =
  | 'CONFIRMED'
  | 'FAILED'
  | 'BLOCKED'
  | 'REJECTED'
  | 'AWAITING_APPROVAL'
  | 'IN_PROGRESS'
  | 'SIMULATED'

export type ReceiptStep = {
  label: string
  state: 'DONE' | 'FAILED' | 'WAITING' | 'NOT_CREATED'
  at: string | null
  detail: string | null
  auditEventId: string | null
}

export type AuditProof = {
  id: string
  action: string
  status: string
  createdAt: string | null
  anchorStatus: AnchorStatus
  topicId: string | null
  sequenceNumber: number | null
  /** true only when read back from HCS with a matching hash; null when never anchored. */
  verified: boolean | null
  verificationDetail: string | null
  explorerUrl: string | null
}

export type LedgerCheck = { name: string; expected: string; actual: string; ok: boolean }

export type PaymentVerification = {
  verified: boolean
  detail: string
  paymentStatus: PaymentStatus
  transactionId: string | null
  ledgerResult: string | null
  consensusTimestamp: string | null
  checks: LedgerCheck[]
  explorerUrl: string | null
}

export type PaymentReceipt = {
  payment: Payment
  outcome: ReceiptOutcome
  headline: string
  detail: string
  onLedger: boolean
  ledger: PaymentVerification | null
  timeline: ReceiptStep[]
  audit: AuditProof[]
  /** Only facts a backend check just confirmed. */
  badges: { policyChecked: boolean; ledgerVerified: boolean; auditVerified: boolean }
  network: string
}

export async function approvePayment(id: string): Promise<Payment> {
  return request(`/payments/${encodeURIComponent(id)}/approve`, { method: 'POST' })
}

export async function rejectPayment(id: string): Promise<Payment> {
  return request(`/payments/${encodeURIComponent(id)}/reject`, { method: 'POST' })
}

/** Why the policy decided, with the numbers it decided on. Comes from the backend as is. */
export type PolicyExplanation = {
  verdict: 'ALLOW' | 'HOLD' | 'DENY'
  ruleId: string | null
  reason: string | null
  envelope: string | null
  asset: string
  requested: string | null
  /** What the envelope held when the policy decided; null without an envelope. */
  available: string | null
  /** How much more than `available` was asked; null when it fitted. */
  shortfall: string | null
  transactionCreated: boolean
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
  policyExplanation: PolicyExplanation | null
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
