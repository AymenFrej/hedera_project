import { useCallback, useEffect, useState, type FormEvent } from 'react'
import {
  ArrowLeftRight,
  Check,
  ExternalLink,
  Loader2,
  Plus,
  RefreshCw,
  ShieldAlert,
  ShieldCheck,
  X,
  XCircle,
} from 'lucide-react'
import {
  approvePayment,
  createPayment,
  getPaymentStatus,
  listPayments,
  rejectPayment,
  type Payment,
  type PaymentStatus,
} from '../api/client'

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

export default function PaymentsPage() {
  const [payments, setPayments] = useState<Payment[]>([])
  const [ledgerActive, setLedgerActive] = useState<boolean | null>(null)
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState<string | null>(null)
  const [showForm, setShowForm] = useState(false)
  const [form, setForm] = useState(EMPTY_FORM)
  const [submitting, setSubmitting] = useState(false)
  const [busyId, setBusyId] = useState<string | null>(null)

  const refresh = useCallback(async () => {
    setError(null)
    try {
      const [status, list] = await Promise.all([getPaymentStatus(), listPayments()])
      setLedgerActive(status.ledgerActive)
      setPayments(list)
    } catch (e) {
      setError(errorMessage(e, 'Backend unreachable'))
    } finally {
      setLoading(false)
    }
  }, [])

  useEffect(() => {
    void refresh()
  }, [refresh])

  async function handleSubmit(event: FormEvent) {
    event.preventDefault()
    setSubmitting(true)
    setError(null)
    try {
      await createPayment({
        destination: form.destination.trim(),
        amount: form.amount.trim(),
        tokenId: form.tokenId.trim() || undefined,
        envelope: form.envelope || undefined,
        memo: form.memo.trim() || undefined,
      })
      setForm(EMPTY_FORM)
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
        <form className="panel payment-form" onSubmit={(e) => void handleSubmit(e)}>
          <label>
            Destination account
            <input
              required
              placeholder="0.0.12345"
              value={form.destination}
              onChange={(e) => setForm({ ...form, destination: e.target.value })}
            />
          </label>
          <label>
            Amount
            <input
              required
              inputMode="decimal"
              placeholder={form.tokenId ? '500 (smallest unit)' : '10 HBAR'}
              value={form.amount}
              onChange={(e) => setForm({ ...form, amount: e.target.value })}
            />
          </label>
          <label>
            Token id <span className="optional">optional, empty = HBAR</span>
            <input
              placeholder="0.0.67890"
              value={form.tokenId}
              onChange={(e) => setForm({ ...form, tokenId: e.target.value })}
            />
          </label>
          <label>
            Envelope
            <select
              value={form.envelope}
              onChange={(e) => setForm({ ...form, envelope: e.target.value })}
            >
              {ENVELOPES.map((env) => (
                <option key={env} value={env}>
                  {env || 'None'}
                </option>
              ))}
            </select>
          </label>
          <label className="wide">
            Memo <span className="optional">optional, max 100 characters</span>
            <input
              maxLength={100}
              value={form.memo}
              onChange={(e) => setForm({ ...form, memo: e.target.value })}
            />
          </label>
          <div className="form-actions">
            <button type="button" className="button secondary" onClick={() => setShowForm(false)}>
              Cancel
            </button>
            <button type="submit" className="button primary" disabled={submitting}>
              {submitting ? <Loader2 size={15} className="spin" /> : <ArrowLeftRight size={15} />}
              Send payment
            </button>
          </div>
        </form>
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
}: {
  payment: Payment
  busy: boolean
  onDecide: (action: 'approve' | 'reject') => void
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
              {p.amount} {p.currency === 'HBAR' ? 'ℏ' : p.currency} → {p.destination}
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
