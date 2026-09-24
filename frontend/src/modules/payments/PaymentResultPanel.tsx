import { useCallback, useEffect, useState } from 'react'
import {
  CheckCircle2,
  CircleDashed,
  ExternalLink,
  Loader2,
  MinusCircle,
  RefreshCw,
  ShieldCheck,
  X,
  XCircle,
} from 'lucide-react'
import {
  getPaymentResult,
  verifyPaymentAuditEvent,
  type AuditProof,
  type PaymentReceipt,
  type ReceiptOutcome,
  type ReceiptStep,
} from '../../api/client'
import PolicyProtection from './PolicyProtection'
import SafetySummary from './SafetySummary'

const TONE: Record<ReceiptOutcome, 'ok' | 'warn' | 'danger'> = {
  CONFIRMED: 'ok',
  FAILED: 'danger',
  BLOCKED: 'danger',
  REJECTED: 'danger',
  AWAITING_APPROVAL: 'warn',
  IN_PROGRESS: 'warn',
  SIMULATED: 'warn',
  CONDITION_NOT_MET: 'danger',
}

/**
 * The result of one payment. Everything shown comes from the backend's receipt: the timeline is the
 * payment's own audit events, and a badge or a "verified" mark appears only after a backend check
 * actually passed.
 */
export default function PaymentResultPanel({
  paymentId,
  onClose,
}: {
  paymentId: string
  onClose: () => void
}) {
  const [receipt, setReceipt] = useState<PaymentReceipt | null>(null)
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState<string | null>(null)
  const [proofs, setProofs] = useState<Record<string, AuditProof>>({})
  const [verifying, setVerifying] = useState<string | null>(null)

  const load = useCallback(async () => {
    setLoading(true)
    setError(null)
    try {
      const r = await getPaymentResult(paymentId)
      setReceipt(r)
      setProofs(Object.fromEntries(r.audit.map((a) => [a.id, a])))
    } catch (e) {
      setError(e instanceof Error ? e.message : 'Could not load the result')
    } finally {
      setLoading(false)
    }
  }, [paymentId])

  useEffect(() => {
    void load()
  }, [load])

  async function verifyEvent(eventId: string) {
    setVerifying(eventId)
    try {
      const v = await verifyPaymentAuditEvent(paymentId, eventId)
      setProofs((current) => ({
        ...current,
        [eventId]: {
          ...current[eventId],
          verified: v.verified,
          verificationDetail: v.detail,
          explorerUrl: v.explorerUrl,
        },
      }))
    } catch (e) {
      setProofs((current) => ({
        ...current,
        [eventId]: {
          ...current[eventId],
          verified: false,
          verificationDetail: e instanceof Error ? e.message : 'Verification failed',
        },
      }))
    } finally {
      setVerifying(null)
    }
  }

  if (!receipt) {
    return (
      <div className="panel result-panel">
        {error ? (
          <div className="audit-banner danger">
            <XCircle size={16} />
            <div>
              <b>Could not load the result</b>
              <span>{error}</span>
            </div>
          </div>
        ) : (
          <div className="empty-state">
            <Loader2 size={20} className="spin" />
            <p>Reading the ledger and the audit trail…</p>
          </div>
        )}
      </div>
    )
  }

  const p = receipt.payment
  const asset = p.currency === 'HBAR' ? 'ℏ' : p.assetSymbol
  const allAudit = Object.values(proofs)
  const auditVerified = allAudit.length > 0 && allAudit.every((a) => a.verified === true)

  return (
    <div className={`panel result-panel ${TONE[receipt.outcome]}`}>
      <div className="result-head">
        <div>
          <p className="eyebrow">PAYMENT RESULT · {receipt.network.toUpperCase()}</p>
          <h3>{receipt.headline}</h3>
          <span className="audit-meta">{receipt.detail}</span>
        </div>
        <div className="result-actions">
          <button className="button secondary small" onClick={() => void load()} disabled={loading}>
            {loading ? <Loader2 size={13} className="spin" /> : <RefreshCw size={13} />}
            {receipt.outcome === 'IN_PROGRESS' ? 'Check status' : 'Re-check'}
          </button>
          <button className="button secondary small" onClick={onClose} aria-label="Close">
            <X size={13} />
          </button>
        </div>
      </div>

      <div className="result-summary">
        <b>
          {p.amount} {asset}
        </b>
        <span className="audit-meta">
          {p.sourceAccount ?? 'not sent'} → {p.destination}
        </span>
        {p.transactionId && receipt.onLedger && <code>{p.transactionId}</code>}
      </div>

      <Badges
        policyChecked={receipt.badges.policyChecked}
        ledgerVerified={receipt.badges.ledgerVerified}
        auditVerified={auditVerified}
      />

      {p.policyExplanation &&
        (p.policyExplanation.verdict === 'DENY' ||
          receipt.outcome === 'AWAITING_APPROVAL' ||
          receipt.outcome === 'REJECTED') && (
        <PolicyProtection
          why={p.policyExplanation}
          asset={asset}
          whenNotSent="NOT CREATED: nothing was sent, no fee was paid"
        />
      )}
      {receipt.outcome === 'REJECTED' && p.failureReason && (
        <div className="audit-banner danger">
          <MinusCircle size={16} />
          <div>
            <b>Not sent</b>
            <span>{p.failureReason}</span>
          </div>
        </div>
      )}

      <SafetySummary rows={receipt.safety} />

      <div className="result-grid">
        <div>
          <p className="eyebrow">WHAT HAPPENED</p>
          <ol className="timeline">
            {receipt.timeline.map((step, i) => (
              <TimelineStep
                key={i}
                step={step}
                proof={step.auditEventId ? proofs[step.auditEventId] : undefined}
                verifying={verifying === step.auditEventId}
                onVerify={() => step.auditEventId && void verifyEvent(step.auditEventId)}
              />
            ))}
          </ol>
        </div>

        <div>
          <p className="eyebrow">LEDGER · MIRROR NODE</p>
          {receipt.ledger ? (
            <>
              <span className="audit-meta">{receipt.ledger.detail}</span>
              <ul className="preview-checks ledger-checks">
                {receipt.ledger.checks.map((c) => (
                  <li key={c.name} className={c.ok ? 'pass' : 'fail'}>
                    {c.ok ? <CheckCircle2 size={13} /> : <XCircle size={13} />}
                    <div>
                      <b>{c.name}</b>
                      <span>
                        expected {c.expected} · found {c.actual}
                      </span>
                    </div>
                  </li>
                ))}
              </ul>
            </>
          ) : (
            <span className="audit-meta">
              {p.transactionId ? 'Not checked on the ledger.' : 'No transaction to check: nothing was sent to Hedera.'}
            </span>
          )}
          {p.explorerUrl && (
            <a className="button secondary small hashscan" href={p.explorerUrl} target="_blank" rel="noreferrer">
              View on HashScan <ExternalLink size={12} />
            </a>
          )}
        </div>
      </div>
    </div>
  )
}

/** A badge exists only for a check that just passed; nothing is shown for the others. */
function Badges(b: { policyChecked: boolean; ledgerVerified: boolean; auditVerified: boolean }) {
  const shown = [
    b.policyChecked && 'Policy checked',
    b.ledgerVerified && 'Ledger verified',
    b.auditVerified && 'Audit verified on HCS',
  ].filter(Boolean) as string[]
  if (shown.length === 0) return null
  return (
    <div className="result-badges">
      {shown.map((label) => (
        <span key={label} className="anchor-badge ok">
          <ShieldCheck size={11} /> {label}
        </span>
      ))}
    </div>
  )
}

function TimelineStep({
  step,
  proof,
  verifying,
  onVerify,
}: {
  step: ReceiptStep
  proof?: AuditProof
  verifying: boolean
  onVerify: () => void
}) {
  const icon =
    step.state === 'DONE' ? (
      <CheckCircle2 size={15} />
    ) : step.state === 'FAILED' ? (
      <XCircle size={15} />
    ) : step.state === 'NOT_CREATED' ? (
      <MinusCircle size={15} />
    ) : (
      <CircleDashed size={15} />
    )
  return (
    <li className={`timeline-step ${step.state.toLowerCase()}`}>
      <span className="timeline-icon">{icon}</span>
      <div>
        <b>{step.label}</b>
        {step.detail && <span className="audit-meta">{step.detail}</span>}
        {step.at && <span className="audit-meta">{new Date(step.at).toLocaleString()}</span>}
        {proof && <ProofLine proof={proof} verifying={verifying} onVerify={onVerify} />}
      </div>
    </li>
  )
}

function ProofLine({
  proof,
  verifying,
  onVerify,
}: {
  proof: AuditProof
  verifying: boolean
  onVerify: () => void
}) {
  if (proof.anchorStatus !== 'ANCHORED') {
    return <span className="proof-line muted">{proof.verificationDetail}</span>
  }
  return (
    <span className="proof-line">
      <span>
        HCS {proof.topicId} #{proof.sequenceNumber}
      </span>
      {proof.verified === true && (
        <span className="proof-ok">
          <CheckCircle2 size={11} /> verified
        </span>
      )}
      {proof.verified === false && (
        <span className="proof-bad" title={proof.verificationDetail ?? ''}>
          <XCircle size={11} /> {proof.verificationDetail}
        </span>
      )}
      <button className="text-button" onClick={onVerify} disabled={verifying}>
        {verifying ? <Loader2 size={11} className="spin" /> : null} Verify on HCS
      </button>
      {proof.explorerUrl && (
        <a className="text-button" href={proof.explorerUrl} target="_blank" rel="noreferrer">
          topic <ExternalLink size={10} />
        </a>
      )}
    </span>
  )
}
