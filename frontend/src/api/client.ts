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
export async function editManagedProfile(id: string, email: string, displayName: string) { return request<ManagedUser>(`/admin/users/${id}/profile`, {method: 'PUT', body: JSON.stringify({email, displayName})}) }
export type AccountAgentResult = { taskId: string; status: string; message: string; data: Record<string, unknown> }
export async function runAccountAgent(prompt: string): Promise<AccountAgentResult> {
  return request('/admin/users/agent', { method: 'POST', body: JSON.stringify({ requestId: crypto.randomUUID(), intent: 'CREATE_ACCOUNT', prompt, context: {} }) })
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
  /** true: payments leave from each person's own wallet; false: from the platform account. */
  paysFromUserWallets?: boolean
  /** Where top-ups come from; null in simulation. */
  treasuryAccount?: string | null
}

/** Treasury HBAR into your own wallet; held until another administrator approves it. */
export async function requestTopUp(amount: string, idempotencyKey: string): Promise<Payment> {
  return request('/payments/top-up', {
    method: 'POST',
    body: JSON.stringify({ amount }),
    headers: { 'Idempotency-Key': idempotencyKey },
  })
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
  | 'CONDITION_NOT_MET'

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

/** One line of the safety summary, from a fact the backend holds or just checked. */
export type SafetyRow = {
  name: string
  state: 'PASS' | 'FAIL' | 'WAITING' | 'NOT_APPLICABLE' | 'UNKNOWN'
  detail: string
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
  safety: SafetyRow[]
  network: string
}

export async function approvePayment(id: string): Promise<Payment> {
  return request(`/payments/${encodeURIComponent(id)}/approve`, { method: 'POST' })
}

export async function rejectPayment(id: string): Promise<Payment> {
  return request(`/payments/${encodeURIComponent(id)}/reject`, { method: 'POST' })
}

/** Why the policy decided, with the numbers it decided on. Comes from the backend as is. */
/** A name someone can be paid by, tied to one Hedera account. */
export type Contact = { id: string; name: string; accountId: string }

export async function listContacts(): Promise<Contact[]> {
  return request('/payments/contacts')
}

export async function addContact(name: string, accountId: string): Promise<Contact> {
  return request('/payments/contacts', { method: 'POST', body: JSON.stringify({ name, accountId }) })
}

export async function deleteContact(id: string): Promise<void> {
  return request(`/payments/contacts/${encodeURIComponent(id)}`, { method: 'DELETE' })
}

/**
 * What someone wants to pay, before anything is resolved: the contract the future orchestrator
 * will produce from free text.
 */
export type PaymentIntent = {
  recipient: string
  amount: string
  asset: string | null
  envelope: string | null
  memo: string | null
  keepAtLeast: string | null
}

export type IntentUnderstanding = {
  understood: boolean
  request: CreatePaymentRequest | null
  recipientName: string | null
  assetSymbol: string | null
  steps: { field: string; input: string; value: string | null; source: string }[]
  problems: string[]
  /** The policy's envelopes to pick from when none (or an unknown one) was said. */
  envelopeChoices: string[]
}

/** What became of a sentence: what the language model read, then what the application resolved. */
export type SentenceInterpretation = {
  /** false when no language model is configured on the backend. */
  available: boolean
  detail: string | null
  /** The model that read the sentence. */
  source: string | null
  intent: PaymentIntent | null
  clarification: string | null
  understanding: IntentUnderstanding | null
  /** The attached file's name; null for a sentence. */
  document: string | null
  /** Something to check before confirming, e.g. an account read from a PDF. */
  warning: string | null
}

/** The language model only proposes fields; the backend resolves and checks them like a form. */
export async function interpretSentence(text: string): Promise<SentenceInterpretation> {
  return request('/payments/intent/interpret', { method: 'POST', body: JSON.stringify({ text }) })
}

/** The same as a sentence, from an attached invoice or bill (base64, at most 5 MB). */
export async function interpretDocument(
  fileName: string,
  mimeType: string,
  content: string,
  note: string,
): Promise<SentenceInterpretation> {
  return request('/payments/intent/document', {
    method: 'POST',
    body: JSON.stringify({ fileName, mimeType, content, note }),
  })
}

/** Resolves each field of an intent on the backend, with where each value came from. */
export async function understandIntent(intent: PaymentIntent): Promise<IntentUnderstanding> {
  return request('/payments/intent/understand', { method: 'POST', body: JSON.stringify(intent) })
}

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
  /** How a person writes it: "HBAR" or the token symbol (falls back to the token id). */
  assetSymbol: string
  tokenId: string | null
  destination: string
  envelope: string | null
  memo: string | null
  keepAtLeast: string | null
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
  /** PAYMENT, or TOP_UP (treasury HBAR to the requester's own wallet) */
  kind?: 'PAYMENT' | 'TOP_UP'
  createdAt: string | null
  updatedAt: string | null
}

export type CreatePaymentRequest = {
  destination: string
  amount: string
  tokenId?: string
  envelope?: string | null
  memo?: string | null
  /** The requester's own condition: refuse if less than this would remain. */
  keepAtLeast?: string | null
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

// --- Tokens -------------------------------------------------------------------------------------

/** A token as designed in the Studio; everything is text so nothing is rounded on the way in. */
export type TokenDraft = {
  name: string
  symbol: string
  decimals: string
  initialSupply: string
  /** FIXED (never more), CAPPED (mintable up to maxSupply) or UNLIMITED */
  supplyPolicy: string
  maxSupply: string | null
  memo: string | null
}

/** Read from the token's keys: GUARANTEE (it can never...) or POWER (someone can...). */
export type TokenPromise = { kind: 'GUARANTEE' | 'POWER'; title: string; detail: string }

export type TokenPreview = {
  valid: boolean
  problems: string[]
  warnings: string[]
  name: string | null
  symbol: string | null
  decimals: number
  initialSupply: string | null
  maxSupply: string | null
  supplyType: string | null
  mintable: boolean
  memo: string | null
  treasury: string | null
  live: boolean
  promises: TokenPromise[]
  steps: string[]
}

export type TokenOperation = {
  id: string
  kind: 'CREATE' | 'MINT'
  status: 'SUBMITTING' | 'CONFIRMED' | 'FAILED' | 'UNKNOWN' | 'SIMULATED'
  tokenId: string | null
  name: string | null
  symbol: string | null
  decimals: number | null
  amount: string | null
  maxSupply: string | null
  mintable: boolean | null
  memo: string | null
  treasury: string | null
  transactionId: string | null
  networkStatus: string | null
  totalSupplyAfter: string | null
  failureReason: string | null
  /** What the network really charged, read back from the Mirror Node */
  feeHbar: string | null
  consensusTimestamp: string | null
  requestedById: string | null
  createdAt: string | null
  transactionUrl: string | null
  tokenUrl: string | null
}

export type TokenCard = {
  tokenId: string
  symbol: string | null
  name: string | null
  decimals: number
  balance: string
  createdHere: boolean
}

export type TokenPortfolio = {
  live: boolean
  treasury: string | null
  asOf: string | null
  mirrorAvailable: boolean
  tokens: TokenCard[]
}

export type TokenHolder = { account: string; balance: string; share: number; treasury: boolean }

export type TokenPassport = {
  tokenId: string
  name: string | null
  symbol: string | null
  decimals: number
  type: string | null
  totalSupply: string
  maxSupply: string | null
  supplyType: string | null
  treasury: string | null
  memo: string | null
  createdAt: string | null
  pauseStatus: string | null
  promises: TokenPromise[]
  holders: TokenHolder[]
  holdersComplete: boolean
  treasuryShare: number
  canMint: boolean
  mintReason: string | null
  operations: TokenOperation[]
  tokenUrl: string
}

export type MintPreview = {
  valid: boolean
  problems: string[]
  tokenId: string
  symbol: string | null
  decimals: number
  amount: string | null
  supplyBefore: string | null
  supplyAfter: string | null
  maxSupply: string | null
  live: boolean
}

export type Receivability = {
  accountId: string
  tokenId: string
  state: 'CAN_RECEIVE' | 'AUTO_ASSOCIATES' | 'NEEDS_ASSOCIATION' | 'NO_ACCOUNT' | 'DELETED'
  canReceive: boolean
  detail: string
}

export type TokenVerification = {
  state: 'VERIFIED' | 'MISMATCH' | 'PENDING' | 'NOT_SUBMITTED' | 'UNAVAILABLE'
  summary: string
  checks: { label: string; passed: boolean; detail: string }[]
  feeHbar: string | null
  consensusTimestamp: string | null
  transactionUrl: string | null
}

export type TokenInterpretation = {
  available: boolean
  detail: string
  source: string | null
  draft: Partial<TokenDraft> | null
  clarification: string | null
  preview: TokenPreview | null
}

export async function getTokenPortfolio(): Promise<TokenPortfolio> {
  return request('/tokens')
}

export async function previewToken(draft: TokenDraft): Promise<TokenPreview> {
  return request('/tokens/preview', { method: 'POST', body: JSON.stringify(draft) })
}

export async function createToken(draft: TokenDraft, idempotencyKey: string): Promise<TokenOperation> {
  return request('/tokens', {
    method: 'POST',
    body: JSON.stringify(draft),
    headers: { 'Idempotency-Key': idempotencyKey },
  })
}

/** The Token Agent reads a sentence into the Studio's fields; nothing is created. */
export async function interpretTokenSentence(text: string): Promise<TokenInterpretation> {
  return request('/tokens/intent/interpret', { method: 'POST', body: JSON.stringify({ text }) })
}

export async function listTokenOperations(): Promise<TokenOperation[]> {
  return request('/tokens/operations')
}

export async function verifyTokenOperation(id: string): Promise<TokenVerification> {
  return request(`/tokens/operations/${encodeURIComponent(id)}/verification`)
}

export async function getTokenPassport(tokenId: string): Promise<TokenPassport> {
  return request(`/tokens/${encodeURIComponent(tokenId)}/passport`)
}

export async function previewMint(tokenId: string, amount: string): Promise<MintPreview> {
  return request(`/tokens/${encodeURIComponent(tokenId)}/mint/preview`, {
    method: 'POST',
    body: JSON.stringify({ amount }),
  })
}

export async function mintToken(tokenId: string, amount: string, idempotencyKey: string): Promise<TokenOperation> {
  return request(`/tokens/${encodeURIComponent(tokenId)}/mint`, {
    method: 'POST',
    body: JSON.stringify({ amount }),
    headers: { 'Idempotency-Key': idempotencyKey },
  })
}

export async function checkReceivable(tokenId: string, accountId: string): Promise<Receivability> {
  return request(`/tokens/${encodeURIComponent(tokenId)}/receivable/${encodeURIComponent(accountId)}`)
}
