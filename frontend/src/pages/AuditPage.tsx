import { useCallback, useEffect, useState } from 'react'
import {
  Activity,
  CheckCircle2,
  ExternalLink,
  Loader2,
  Plus,
  RefreshCw,
  ShieldAlert,
  ShieldCheck,
  UserRound,
  XCircle,
} from 'lucide-react'
import {
  getAuthUser,
  getAuditStatus,
  listAuditEvents,
  recordAuditEvent,
  verifyAuditEvent,
  type AuditEvent,
  type VerificationResult,
} from '../api/client'

type VerificationState = { loading: boolean; result?: VerificationResult }

export default function AuditPage() {
  const [events, setEvents] = useState<AuditEvent[]>([])
  const [ledgerActive, setLedgerActive] = useState<boolean | null>(null)
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState<string | null>(null)
  const [recording, setRecording] = useState(false)
  const [verifications, setVerifications] = useState<Record<string, VerificationState>>({})

  const refresh = useCallback(async () => {
    setError(null)
    try {
      const [status, list] = await Promise.all([getAuditStatus(), listAuditEvents()])
      setLedgerActive(status.ledgerActive)
      setEvents(list)
    } catch (e) {
      setError(e instanceof Error ? e.message : 'Backend unreachable')
    } finally {
      setLoading(false)
    }
  }, [])

  useEffect(() => {
    void refresh()
  }, [refresh])

  async function handleRecord() {
    setRecording(true)
    setError(null)
    try {
      await recordAuditEvent({
        agent: 'PaymentAgent',
        action: 'TRANSFER',
        status: 'SUCCESS',
        metadata: { amount: '50', destination: '0.0.999' },
      })
      await refresh()
    } catch (e) {
      setError(e instanceof Error ? e.message : 'Could not record the event')
    } finally {
      setRecording(false)
    }
  }

  async function handleVerify(id: string) {
    setVerifications((current) => ({ ...current, [id]: { loading: true } }))
    try {
      const result = await verifyAuditEvent(id)
      setVerifications((current) => ({ ...current, [id]: { loading: false, result } }))
    } catch (e) {
      setVerifications((current) => ({
        ...current,
        [id]: {
          loading: false,
          result: {
            verified: false,
            detail: e instanceof Error ? e.message : 'Verification failed',
            storedHash: null,
            ledgerHash: null,
            storedPayload: null,
            ledgerPayload: null,
            consensusTimestamp: null,
            explorerUrl: null,
          },
        },
      }))
    }
  }

  return (
    <>
      <div className="page-heading">
        <div>
          <p className="eyebrow">MODULE / AUDIT</p>
          <h1>Audit &amp; Monitoring</h1>
          <p className="subtitle">
            Every agent action is written to a Hedera topic, then verified by reading it back from
            the Mirror Node.
          </p>
        </div>
        <div className="page-actions">
          <button className="button secondary" onClick={() => void refresh()} disabled={loading}>
            <RefreshCw size={15} /> Refresh
          </button>
          {['ADMIN', 'PLATFORM'].includes(getAuthUser()?.role ?? '') && <button className="button primary" onClick={() => void handleRecord()} disabled={recording}>
            {recording ? <Loader2 size={15} className="spin" /> : <Plus size={15} />}
            Record test event
          </button>}
        </div>
      </div>

      <LedgerBanner ledgerActive={ledgerActive} />

      {error && (
        <div className="audit-banner danger">
          <XCircle size={16} />
          <div>
            <b>{error}</b>
            <span>Is the backend running on {import.meta.env.VITE_API_BASE_URL ?? 'http://localhost:8080/api/v1'}?</span>
          </div>
        </div>
      )}

      <div className="data-panel">
        {loading ? (
          <div className="empty-state">
            <Loader2 size={20} className="spin" />
            <p>Loading audit events…</p>
          </div>
        ) : events.length === 0 ? (
          <div className="empty-state">
            <div className="empty-icon">
              <Activity size={20} />
            </div>
            <p>No audit events yet.</p>
            <span>Record one to see it anchored on Hedera.</span>
          </div>
        ) : (
          <div className="data-list">
            {events.map((event) => (
              <AuditRow
                key={event.id}
                event={event}
                state={verifications[event.id]}
                onVerify={() => void handleVerify(event.id)}
              />
            ))}
          </div>
        )}
      </div>
    </>
  )
}

function LedgerBanner({ ledgerActive }: { ledgerActive: boolean | null }) {
  if (ledgerActive === null) return null
  return ledgerActive ? (
    <div className="audit-banner ok">
      <ShieldCheck size={16} />
      <div>
        <b>Ledger active</b>
        <span>Events are submitted to Hedera and can be verified.</span>
      </div>
    </div>
  ) : (
    <div className="audit-banner warn">
      <ShieldAlert size={16} />
      <div>
        <b>Ledger inactive — events are stored locally only</b>
        <span>
          Set HEDERA_OPERATOR_ID and HEDERA_OPERATOR_PRIVATE_KEY to anchor events. Nothing here is
          proof while this banner is showing.
        </span>
      </div>
    </div>
  )
}

function AuditRow({
  event,
  state,
  onVerify,
}: {
  event: AuditEvent
  state?: VerificationState
  onVerify: () => void
}) {
  const anchored = event.anchorStatus === 'ANCHORED'
  return (
    <div className="audit-row">
      <div className="audit-row-main">
        <div className="data-leading">
          <div className="row-icon">
            <Activity size={16} />
          </div>
          <div>
            <b>
              {event.agent} · {event.action}
            </b>
            <span className="audit-meta">
              {event.createdAt ? new Date(event.createdAt).toLocaleString() : 'unknown date'}
              {event.sequenceNumber !== null && <> · seq {event.sequenceNumber}</>}
            </span>
            {event.actorId && (
              <span className="actor-chip">
                <UserRound size={11} />
                {event.actorType}
                {' · '}
                {event.actorId}
                {event.actorHederaAccountId && <span className="mono"> ({event.actorHederaAccountId})</span>}
              </span>
            )}
          </div>
        </div>
        <div className="row-meta">
          <span className={`anchor-badge ${event.anchorStatus.toLowerCase()}`}>
            {event.anchorStatus}
          </span>
          <button className="button secondary small" onClick={onVerify} disabled={!anchored || state?.loading}>
            {state?.loading ? <Loader2 size={14} className="spin" /> : <ShieldCheck size={14} />}
            Verify
          </button>
        </div>
      </div>

      {anchored && (
        <dl className="audit-proof">
          <div>
            <dt>Topic</dt>
            <dd>{event.topicId}</dd>
          </div>
          <div>
            <dt>Transaction</dt>
            <dd className="mono">{event.transactionId}</dd>
          </div>
          <div>
            <dt>Consensus</dt>
            <dd className="mono">{event.consensusTimestamp}</dd>
          </div>
          <div>
            <dt>Payload SHA-256</dt>
            <dd className="mono truncate" title={event.payloadHash ?? undefined}>
              {event.payloadHash}
            </dd>
          </div>
        </dl>
      )}

      {state?.result && <VerificationPanel result={state.result} />}
    </div>
  )
}

function VerificationPanel({ result }: { result: VerificationResult }) {
  const mismatch =
    !result.verified && result.storedHash !== null && result.ledgerHash !== null

  return (
    <div className={`verification ${result.verified ? 'ok' : 'danger'}`}>
      <div className="verification-head">
        {result.verified ? <CheckCircle2 size={16} /> : <XCircle size={16} />}
        <b>{result.verified ? 'Verified against the ledger' : 'Tampering detected'}</b>
        {result.explorerUrl && (
          <a href={result.explorerUrl} target="_blank" rel="noreferrer" className="text-button">
            View on HashScan <ExternalLink size={13} />
          </a>
        )}
      </div>
      <p>{result.detail}</p>

      {mismatch ? (
        <div className="hash-compare">
          <div className="hash-side bad">
            <span className="hash-label">In our database (altered)</span>
            <code>{result.storedHash}</code>
            {result.storedPayload && <pre className="ledger-payload">{result.storedPayload}</pre>}
          </div>
          <div className="hash-side good">
            <span className="hash-label">On the ledger (unchangeable)</span>
            <code>{result.ledgerHash}</code>
            {result.ledgerPayload && <pre className="ledger-payload">{result.ledgerPayload}</pre>}
          </div>
        </div>
      ) : (
        result.ledgerPayload && <pre className="ledger-payload">{result.ledgerPayload}</pre>
      )}
    </div>
  )
}
