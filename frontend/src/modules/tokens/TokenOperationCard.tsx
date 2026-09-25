import { useState } from 'react'
import { CheckCircle2, Clock, ExternalLink, Loader2, SearchCheck, XCircle } from 'lucide-react'
import { verifyTokenOperation, type TokenOperation, type TokenVerification } from '../../api/client'
import TokenCoin from './TokenCoin'

const STATUS: Record<TokenOperation['status'], { label: string; tone: string }> = {
  CONFIRMED: { label: 'Confirmed by Hedera', tone: 'ok' },
  FAILED: { label: 'Refused by Hedera', tone: 'danger' },
  UNKNOWN: { label: 'No receipt yet', tone: 'warn' },
  SUBMITTING: { label: 'Sending', tone: 'warn' },
  SIMULATED: { label: 'Simulated: nothing sent', tone: 'warn' },
}

/**
 * One creation or mint, with what the ledger says about it. "Verify" compares it with the Mirror
 * Node and reads the fee the network really charged; an operation left without receipt is settled.
 */
export default function TokenOperationCard({
  op,
  onSettled,
}: {
  op: TokenOperation
  onSettled?: () => void
}) {
  const [verification, setVerification] = useState<TokenVerification | null>(null)
  const [busy, setBusy] = useState(false)
  const [error, setError] = useState<string | null>(null)
  const status = STATUS[op.status]

  async function verify() {
    setBusy(true)
    setError(null)
    try {
      const v = await verifyTokenOperation(op.id)
      setVerification(v)
      if (op.status === 'UNKNOWN' || op.status === 'SUBMITTING') onSettled?.()
    } catch (e) {
      setError(e instanceof Error ? e.message : 'Verification failed')
    } finally {
      setBusy(false)
    }
  }

  const what =
    op.kind === 'CREATE'
      ? `Created ${op.name ?? ''} (${op.symbol ?? '?'}): ${op.amount ?? '?'} to the treasury`
      : `Minted ${op.amount ?? '?'} ${op.symbol ?? ''}${op.totalSupplyAfter ? `: supply now ${op.totalSupplyAfter}` : ''}`
  const fee = verification?.feeHbar ?? op.feeHbar
  return (
    <div className={`token-op ${status.tone}`}>
      <div className="token-op-head">
        <TokenCoin symbol={op.symbol} size={38} />
        <div>
          <b>{what}</b>
          <span>
            {op.tokenId ? <code>{op.tokenId}</code> : null} {op.createdAt ? new Date(op.createdAt).toLocaleString() : ''}
          </span>
        </div>
        <span className={`token-status ${status.tone}`}>
          {status.tone === 'ok' ? <CheckCircle2 size={12} /> : status.tone === 'danger' ? <XCircle size={12} /> : <Clock size={12} />}
          {status.label}
        </span>
      </div>
      {op.failureReason && <div className="problem">{op.failureReason}</div>}
      <div className="token-op-actions">
        {op.transactionUrl && (
          <button type="button" className="button secondary small" disabled={busy} onClick={() => void verify()}>
            {busy ? <Loader2 size={12} className="spin" /> : <SearchCheck size={12} />} Verify on the ledger
          </button>
        )}
        {op.transactionUrl && (
          <a className="button secondary small" href={op.transactionUrl} target="_blank" rel="noreferrer">
            Transaction <ExternalLink size={11} />
          </a>
        )}
        {op.tokenUrl && (
          <a className="button secondary small" href={op.tokenUrl} target="_blank" rel="noreferrer">
            Token on HashScan <ExternalLink size={11} />
          </a>
        )}
        {fee && (
          <span className="token-fee" title="Charged by the network, read back from the Mirror Node">
            Real cost: <b>{fee} ℏ</b>
          </span>
        )}
      </div>
      {error && <div className="problem">{error}</div>}
      {verification && (
        <div className={`token-verification ${verification.state === 'VERIFIED' ? 'ok' : verification.state === 'MISMATCH' ? 'danger' : 'warn'}`}>
          <b>{verification.summary}</b>
          {verification.checks.map((c) => (
            <div key={c.label} className="check">
              {c.passed ? <CheckCircle2 size={12} /> : <XCircle size={12} />}
              <span>{c.label}</span>
              <em>{c.detail}</em>
            </div>
          ))}
        </div>
      )}
    </div>
  )
}
