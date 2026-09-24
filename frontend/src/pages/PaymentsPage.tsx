import { useCallback, useEffect, useState, type FormEvent } from 'react'
import {
  AlertTriangle,
  ArrowLeftRight,
  Check,
  CircleHelp,
  Eye,
  ExternalLink,
  Loader2,
  Plus,
  RefreshCw,
  Wallet,
  ShieldAlert,
  ShieldCheck,
  X,
  XCircle,
} from 'lucide-react'
import {
  approvePayment,
  createPayment,
  getPaymentBalance,
  getPaymentStatus,
  listContacts,
  listPayments,
  previewPayment,
  type Contact,
  rejectPayment,
  type Balance,
  type CreatePaymentRequest,
  type PaymentPreview,
  type Payment,
  type PaymentStatus,
  type PaymentsStatus,
} from '../api/client'
import PaymentResultPanel from '../modules/payments/PaymentResultPanel'
import IntentComposer from '../modules/payments/IntentComposer'
import SentenceBox from '../modules/payments/SentenceBox'

const ENVELOPES = ['', 'RENT', 'ESSENTIALS', 'EMERGENCY']

const STATUS_TONE: Record<PaymentStatus, 'ok' | 'pending' | 'failed'> = {
  CONFIRMED: 'ok',
  SIMULATED: 'pending',
  PENDING: 'pending',
  SUBMITTED: 'pending',
  AWAITING_APPROVAL: 'pending',
  REJECTED: 'failed',
  FAILED: 'failed',
}

const EMPTY_FORM = { destination: '', amount: '', tokenId: '', envelope: '', memo: '' }
const OTHER_TOKEN = '__other__'

export default function PaymentsPage() {
  const [payments, setPayments] = useState<Payment[]>([])
  const [status, setStatus] = useState<PaymentsStatus | null>(null)
  const [balance, setBalance] = useState<Balance | null>(null)
  const [customToken, setCustomToken] = useState(false)
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState<string | null>(null)
  const [showForm, setShowForm] = useState(false)
  const [form, setForm] = useState(EMPTY_FORM)
  const [submitting, setSubmitting] = useState(false)
  // One key per payment attempt, kept across retries of that attempt, renewed once it succeeds.
  const [attemptKey, setAttemptKey] = useState(() => crypto.randomUUID())
  // The request a preview was made for: Execute sends exactly this, never the (possibly edited) form.
  const [preview, setPreview] = useState<{ request: CreatePaymentRequest; result: PaymentPreview } | null>(null)
  const [previewing, setPreviewing] = useState(false)
  // The payment whose result is open; set after Execute, or from a row.
  const [resultId, setResultId] = useState<string | null>(null)
  const [contacts, setContacts] = useState<Contact[]>([])

  const loadContacts = useCallback(() => {
    listContacts()
      .then(setContacts)
      .catch(() => setContacts([]))
  }, [])

  useEffect(() => {
    loadContacts()
  }, [loadContacts])

  /** Preview a request that came from the intent card rather than the form. */
  async function previewRequest(request: CreatePaymentRequest) {
    setPreviewing(true)
    setError(null)
    try {
      setPreview({ request, result: await previewPayment(request) })
    } catch (e) {
      setError(errorMessage(e, 'Could not preview the payment'))
    } finally {
      setPreviewing(false)
    }
  }
  const [busyId, setBusyId] = useState<string | null>(null)

  const refresh = useCallback(async () => {
    setError(null)
    try {
      const [s, list] = await Promise.all([getPaymentStatus(), listPayments()])
      setStatus(s)
      setPayments(list)
      // The balance comes from the Mirror Node and may be slower; it never blocks the page.
      getPaymentBalance()
        .then(setBalance)
        .catch(() => setBalance(null))
    } catch (e) {
      setError(errorMessage(e, 'Backend unreachable'))
    } finally {
      setLoading(false)
    }
  }, [])

  useEffect(() => {
    void refresh()
  }, [refresh])

  const ledgerActive = status?.ledgerActive ?? null

  /** Tokens the paying account holds, plus the demo token even before the balance loads. */
  const tokenOptions = (() => {
    const options = new Map<string, string>()
    for (const t of balance?.tokens ?? []) {
      if (t.tokenId) options.set(t.tokenId, `${t.symbol ?? 'Token'} · ${t.tokenId}`)
    }
    if (status?.demoTokenId && !options.has(status.demoTokenId)) {
      options.set(status.demoTokenId, `Demo token · ${status.demoTokenId}`)
    }
    return [...options.entries()]
  })()

  const symbolOf = (tokenId: string | null) =>
    balance?.tokens.find((t) => t.tokenId === tokenId)?.symbol ?? tokenId

  function requestFromForm(): CreatePaymentRequest {
    return {
      destination: form.destination.trim(),
      amount: form.amount.trim(),
      tokenId: form.tokenId.trim() || undefined,
      envelope: form.envelope || undefined,
      memo: form.memo.trim() || undefined,
    }
  }

  /** Any edit makes the preview stale: it no longer describes what Execute would send. */
  function updateForm(next: typeof form) {
    setForm(next)
    setPreview(null)
  }

  async function handlePreview(event: FormEvent) {
    event.preventDefault()
    if (previewing) return
    setPreviewing(true)
    setError(null)
    try {
      const request = requestFromForm()
      setPreview({ request, result: await previewPayment(request) })
    } catch (e) {
      setError(errorMessage(e, 'Could not preview the payment'))
    } finally {
      setPreviewing(false)
    }
  }

  async function handleExecute() {
    if (submitting || !preview) return
    setSubmitting(true)
    setError(null)
    try {
      const created = await createPayment(preview.request, attemptKey)
      setResultId(created.id)
      setPreview(null)
      setForm(EMPTY_FORM)
      setCustomToken(false)
      setAttemptKey(crypto.randomUUID())
      setShowForm(false)
      await refresh()
    } catch (e) {
      setError(errorMessage(e, 'Could not create the payment'))
    } finally {
      setSubmitting(false)
    }
  }

  async function decide(id: string, action: 'approve' | 'reject') {
    setBusyId(id)
    setError(null)
    try {
      await (action === 'approve' ? approvePayment(id) : rejectPayment(id))
      await refresh()
    } catch (e) {
      setError(errorMessage(e, `Could not ${action} the payment`))
    } finally {
      setBusyId(null)
    }
  }

  return (
    <>
      <div className="page-heading">
        <div>
          <p className="eyebrow">MODULE / PAYMENTS</p>
          <h1>Payments &amp; Treasury</h1>
          <p className="subtitle">
            Every payment is checked against policy, sent to Hedera, and written to the audit trail.
          </p>
        </div>
        <div className="page-actions">
          <button className="button secondary" onClick={() => void refresh()} disabled={loading}>
            <RefreshCw size={15} /> Refresh
          </button>
          <button className="button primary" onClick={() => setShowForm((v) => !v)}>
            <Plus size={15} /> New Payment
          </button>
        </div>
      </div>

      <LedgerBanner ledgerActive={ledgerActive} />
      {ledgerActive && <BalanceBar balance={balance} />}

      {error && (
        <div className="audit-banner danger">
          <XCircle size={16} />
          <div>
            <b>Request failed</b>
            <span>{error}</span>
          </div>
        </div>
      )}

      {showForm && (
        <form className="panel payment-form" onSubmit={(e) => void handlePreview(e)}>
          <label>
            Destination account
            {status?.demoRecipientId && (
              <button
                type="button"
                className="text-button inline"
                onClick={() => updateForm({ ...form, destination: status.demoRecipientId ?? '' })}
              >
                use demo recipient
              </button>
            )}
            <input
              required
              placeholder="0.0.12345"
              value={form.destination}
              onChange={(e) => updateForm({ ...form, destination: e.target.value })}
            />
          </label>
          <label>
            Amount
            <input
              required
              inputMode="decimal"
              placeholder={form.tokenId ? `amount in ${symbolOf(form.tokenId)}` : '10 HBAR'}
              value={form.amount}
              onChange={(e) => updateForm({ ...form, amount: e.target.value })}
            />
          </label>
          <label>
            Asset
            <select
              value={customToken ? OTHER_TOKEN : form.tokenId}
              onChange={(e) => {
                const other = e.target.value === OTHER_TOKEN
                setCustomToken(other)
                setPreview(null)
                updateForm({ ...form, tokenId: other ? '' : e.target.value })
              }}
            >
              <option value="">HBAR</option>
              {tokenOptions.map(([id, label]) => (
                <option key={id} value={id}>
                  {label}
                </option>
              ))}
              <option value={OTHER_TOKEN}>Other token id…</option>
            </select>
            {customToken && (
              <input
                required
                placeholder="0.0.67890"
                value={form.tokenId}
                onChange={(e) => updateForm({ ...form, tokenId: e.target.value })}
              />
            )}
          </label>
          <label>
            Envelope
            <select
              value={form.envelope}
              onChange={(e) => updateForm({ ...form, envelope: e.target.value })}
            >
              {ENVELOPES.map((env) => (
                <option key={env} value={env}>
                  {env || 'None (the policy engine will refuse it)'}
                </option>
              ))}
            </select>
          </label>
          <label className="wide">
            Memo <span className="optional">optional, max 100 characters</span>
            <input
              maxLength={100}
              value={form.memo}
              onChange={(e) => updateForm({ ...form, memo: e.target.value })}
            />
          </label>
          <div className="form-actions">
            <button type="button" className="button secondary" onClick={() => setShowForm(false)}>
              Cancel
            </button>
            <button type="submit" className="button primary" disabled={previewing}>
              {previewing ? <Loader2 size={15} className="spin" /> : <Eye size={15} />}
              Preview
            </button>
          </div>
        </form>
      )}

      <SentenceBox
        contacts={contacts}
        tokenSymbols={balance?.tokens.map((t) => t.symbol ?? '') ?? []}
        onPreview={(request) => void previewRequest(request)}
        previewing={previewing}
      />

      <IntentComposer
        contacts={contacts}
        assetSymbols={['HBAR', ...(balance?.tokens.map((t) => t.symbol ?? t.tokenId ?? '') ?? [])]}
        onContactsChanged={loadContacts}
        onPreview={(request) => void previewRequest(request)}
        previewing={previewing}
      />

      {preview && (
        <PreviewPanel
          preview={preview.result}
          executing={submitting}
          onExecute={() => void handleExecute()}
          onCancel={() => setPreview(null)}
        />
      )}

      {resultId && (
        <PaymentResultPanel
          key={resultId}
          paymentId={resultId}
          onClose={() => {
            setResultId(null)
            void refresh()
          }}
        />
      )}

      <div className="section-label">
        <span>PAYMENTS</span>
        <span className="line" />
      </div>

      <div className="data-panel">
        {loading ? (
          <div className="empty-state">
            <Loader2 size={20} className="spin" />
          </div>
        ) : payments.length === 0 ? (
          <div className="empty-state">
            <div className="empty-icon">
              <ArrowLeftRight size={20} />
            </div>
            <p>No payments yet.</p>
            <span>Create one with “New Payment”.</span>
          </div>
        ) : (
          <div className="data-list">
            {payments.map((p) => (
              <PaymentRow
                key={p.id}
                payment={p}
                busy={busyId === p.id}
                onDecide={(action) => void decide(p.id, action)}
                symbolOf={symbolOf}
                onOpen={() => {
                  setResultId(p.id)
                  window.scrollTo({ top: 0, behavior: 'smooth' })
                }}
              />
            ))}
          </div>
        )}
      </div>
    </>
  )
}

function PaymentRow({
  payment: p,
  busy,
  onDecide,
  symbolOf,
  onOpen,
}: {
  payment: Payment
  busy: boolean
  onDecide: (action: 'approve' | 'reject') => void
  symbolOf: (tokenId: string | null) => string | null
  onOpen: () => void
}) {
  const reason = p.failureReason ?? p.policyReason
  return (
    <div className="audit-row">
      <div className="audit-row-main">
        <div className="data-leading">
          <div className="row-icon">
            <ArrowLeftRight size={16} />
          </div>
          <div>
            <b>
              {p.amount} {p.currency === 'HBAR' ? 'ℏ' : p.assetSymbol ?? symbolOf(p.tokenId)} → {p.destination}
            </b>
            <span className="audit-meta">
              {p.envelope ? `${p.envelope} · ` : ''}
              {p.policyRuleId ? `rule ${p.policyRuleId} · ` : ''}
              {p.createdAt ? new Date(p.createdAt).toLocaleString() : ''}
            </span>
            {reason && <span className="audit-meta">{reason}</span>}
          </div>
        </div>
        <div className="row-meta">
          {p.status === 'AWAITING_APPROVAL' && (
            <>
              <button
                className="button small primary"
                disabled={busy}
                onClick={() => onDecide('approve')}
              >
                {busy ? <Loader2 size={13} className="spin" /> : <Check size={13} />} Approve
              </button>
              <button
                className="button small secondary"
                disabled={busy}
                onClick={() => onDecide('reject')}
              >
                <X size={13} /> Reject
              </button>
            </>
          )}
          <button className="text-button" onClick={onOpen}>
            Details
          </button>
          {p.explorerUrl && (
            <a className="text-button" href={p.explorerUrl} target="_blank" rel="noreferrer">
              HashScan <ExternalLink size={12} />
            </a>
          )}
          <span className={`anchor-badge ${STATUS_TONE[p.status]}`}>
            {p.status.replace('_', ' ')}
          </span>
        </div>
      </div>
    </div>
  )
}

const OUTCOME: Record<PaymentPreview['outcome'], { tone: 'ok' | 'warn' | 'danger'; title: string }> = {
  READY: { tone: 'ok', title: 'Ready to send' },
  NEEDS_APPROVAL: { tone: 'warn', title: 'Will wait for approval' },
  BLOCKED: { tone: 'danger', title: 'Blocked by policy' },
  LIKELY_TO_FAIL: { tone: 'danger', title: 'Hedera would refuse this transfer' },
  SIMULATION: { tone: 'warn', title: 'Simulation mode' },
}

/**
 * Shows what the backend returned and nothing else: the policy verdict as the policy gave it, and
 * the ledger checks as the Mirror Node answered them.
 */
function PreviewPanel({
  preview: p,
  executing,
  onExecute,
  onCancel,
}: {
  preview: PaymentPreview
  executing: boolean
  onExecute: () => void
  onCancel: () => void
}) {
  const look = OUTCOME[p.outcome]
  const asset = p.tokenId ? p.symbol ?? p.tokenId : 'ℏ'
  return (
    <div className={`panel preview-panel ${look.tone}`}>
      <div className="preview-head">
        <div>
          <p className="eyebrow">PREVIEW</p>
          <h3>{look.title}</h3>
          <span className="audit-meta">{p.summary}</span>
        </div>
        <div className="preview-amount">
          <b>
            {p.amount} {asset}
          </b>
          <span className="audit-meta">
            {p.payerAccount ?? 'no account'} → {p.destination}
          </span>
        </div>
      </div>

      <div className="preview-grid">
        <div>
          <p className="eyebrow">BALANCE</p>
          {p.balanceBefore ? (
            <dl className="preview-balance">
              <dt>Now</dt>
              <dd>
                {p.balanceBefore} {asset}
              </dd>
              <dt>After</dt>
              <dd>{p.balanceAfter ? `${p.balanceAfter} ${asset}` : 'not enough to pay'}</dd>
            </dl>
          ) : (
            <span className="audit-meta">Not available</span>
          )}
          {!p.tokenId && p.balanceAfter && (
            <span className="audit-meta">Plus the network fee.</span>
          )}
        </div>

        <div>
          <p className="eyebrow">POLICY</p>
          {p.policy.available !== null && (
            <span className="audit-meta">
              Envelope now: {p.policy.available} {asset}
              {p.policy.shortfall ? ` · short by ${p.policy.shortfall} ${asset}` : ''}
            </span>
          )}
          <div className="preview-policy">
            <span className={`anchor-badge ${p.policy.verdict === 'ALLOW' ? 'ok' : p.policy.verdict === 'HOLD' ? 'pending' : 'failed'}`}>
              {p.policy.verdict}
            </span>
            {p.policy.ruleId && <code>{p.policy.ruleId}</code>}
          </div>
          {p.policy.reason && <span className="audit-meta">{p.policy.reason}</span>}
        </div>

        <div>
          <p className="eyebrow">LEDGER CHECKS</p>
          <ul className="preview-checks">
            {p.checks.map((c) => (
              <li key={c.name} className={c.status.toLowerCase()}>
                {c.status === 'PASS' ? (
                  <Check size={13} />
                ) : c.status === 'FAIL' ? (
                  <XCircle size={13} />
                ) : c.status === 'WARN' ? (
                  <AlertTriangle size={13} />
                ) : (
                  <CircleHelp size={13} />
                )}
                <div>
                  <b>{c.name}</b>
                  <span>{c.detail}</span>
                </div>
              </li>
            ))}
          </ul>
        </div>
      </div>

      <div className="preview-foot">
        <span className="audit-meta">{p.note}</span>
        <div className="form-actions">
          <button type="button" className="button secondary" onClick={onCancel}>
            Back
          </button>
          {p.outcome !== 'BLOCKED' && (
            <button
              type="button"
              className={`button ${p.outcome === 'LIKELY_TO_FAIL' ? 'secondary danger' : 'primary'}`}
              disabled={executing}
              onClick={onExecute}
            >
              {executing ? <Loader2 size={15} className="spin" /> : <ArrowLeftRight size={15} />}
              {p.outcome === 'LIKELY_TO_FAIL' ? 'Execute anyway (fee still charged)' : 'Execute'}
            </button>
          )}
        </div>
      </div>
    </div>
  )
}

/** Facts from the Mirror Node only: what the paying account holds, and when. */
function BalanceBar({ balance }: { balance: Balance | null }) {
  if (!balance || !balance.available) {
    return (
      <div className="balance-bar">
        <Wallet size={15} />
        <span className="audit-meta">
          {balance ? balance.detail : 'Loading balance from the Mirror Node…'}
        </span>
      </div>
    )
  }
  return (
    <div className="balance-bar">
      <Wallet size={15} />
      <span className="audit-meta">Paying account</span>
      <b className="mono">{balance.account}</b>
      <span className="balance-asset">{balance.hbar?.amount} ℏ</span>
      {balance.tokens.map((t) => (
        <span key={t.tokenId} className="balance-asset">
          {t.amount} {t.symbol ?? t.tokenId}
        </span>
      ))}
      {balance.asOf && (
        <span className="audit-meta">as of {new Date(balance.asOf).toLocaleTimeString()}</span>
      )}
      {balance.explorerUrl && (
        <a className="text-button" href={balance.explorerUrl} target="_blank" rel="noreferrer">
          HashScan <ExternalLink size={12} />
        </a>
      )}
    </div>
  )
}

function LedgerBanner({ ledgerActive }: { ledgerActive: boolean | null }) {
  if (ledgerActive === null) return null
  return ledgerActive ? (
    <div className="audit-banner ok">
      <ShieldCheck size={16} />
      <div>
        <b>Live on Hedera</b>
        <span>Payments are sent to the network and can be checked on HashScan.</span>
      </div>
    </div>
  ) : (
    <div className="audit-banner warn">
      <ShieldAlert size={16} />
      <div>
        <b>Simulation mode</b>
        <span>
          No Hedera credentials are configured: payments go through policy and audit, but nothing
          is transferred. They are marked SIMULATED.
        </span>
      </div>
    </div>
  )
}

function errorMessage(e: unknown, fallback: string) {
  if (!(e instanceof Error)) return fallback
  try {
    const body = JSON.parse(e.message)
    return body.error ?? body.detail ?? e.message
  } catch {
    return e.message
  }
}
