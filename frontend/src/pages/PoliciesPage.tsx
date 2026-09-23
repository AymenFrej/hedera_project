import { useEffect, useState } from 'react'
import { CheckCircle2, Loader2, PauseCircle, ShieldCheck, XCircle } from 'lucide-react'
import {
  decideSpend,
  getPolicyState,
  type DecideResponse,
  type PolicyStateResponse,
} from '../api/client'

const VERDICT_STYLE = {
  ALLOW: { className: 'ok', icon: CheckCircle2, label: 'ALLOWED' },
  HOLD: { className: 'warn', icon: PauseCircle, label: 'HELD FOR A HUMAN' },
  DENY: { className: 'danger', icon: XCircle, label: 'DENIED' },
} as const

export default function PoliciesPage() {
  const [state, setState] = useState<PolicyStateResponse | null>(null)
  const [envelope, setEnvelope] = useState('RENT')
  const [amount, setAmount] = useState('400')
  const [counterparty, setCounterparty] = useState('landlord-tunis')
  const [decision, setDecision] = useState<DecideResponse | null>(null)
  const [deciding, setDeciding] = useState(false)
  const [error, setError] = useState<string | null>(null)

  useEffect(() => {
    getPolicyState()
      .then(setState)
      .catch(e => setError(e instanceof Error ? e.message : 'Backend unreachable'))
  }, [])

  async function handleDecide() {
    setDeciding(true)
    setError(null)
    try {
      setDecision(await decideSpend({ envelope, amount: Number(amount), counterparty }))
    } catch (e) {
      setError(e instanceof Error ? e.message : 'Backend unreachable')
    } finally {
      setDeciding(false)
    }
  }

  const verdict = decision ? VERDICT_STYLE[decision.verdict] : null
  const VerdictIcon = verdict?.icon

  return (
    <>
      <div className="page-heading">
        <div>
          <p className="eyebrow">MODULE / POLICIES</p>
          <h1>Policies &amp; Approvals</h1>
          <p className="subtitle">
            The agent never decides alone. Every spend is settled by a deterministic rule engine
            and recorded on the audit trail.
          </p>
        </div>
      </div>

      {error && (
        <div className="audit-banner danger">
          <XCircle size={16} />
          <div>
            <b>Backend unreachable</b>
            <span>{error}</span>
          </div>
        </div>
      )}

      <div className="envelope-grid">
        {state &&
          Object.entries(state.envelopes).map(([name, balance]) => (
            <div className="envelope-card" key={name}>
              <span className="hash-label">{name}</span>
              <strong>{balance}</strong>
              <span className="envelope-note">
                {name === 'EMERGENCY' ? 'always needs a human' : 'remaining'}
              </span>
            </div>
          ))}
      </div>

      <div className="data-panel">
        <div className="decide-form">
          <label>
            <span className="hash-label">Envelope</span>
            <select value={envelope} onChange={e => setEnvelope(e.target.value)}>
              {['RENT', 'ESSENTIALS', 'EMERGENCY'].map(name => (
                <option key={name} value={name}>
                  {name}
                </option>
              ))}
            </select>
          </label>
          <label>
            <span className="hash-label">Amount</span>
            <input value={amount} onChange={e => setAmount(e.target.value)} inputMode="numeric" />
          </label>
          <label>
            <span className="hash-label">Counterparty</span>
            <input value={counterparty} onChange={e => setCounterparty(e.target.value)} />
          </label>
          <button className="button primary" onClick={handleDecide} disabled={deciding}>
            {deciding ? <Loader2 size={15} className="spin" /> : <ShieldCheck size={15} />}
            Ask the policy engine
          </button>
        </div>

        {decision && verdict && VerdictIcon && (
          <div className={`verdict-card ${verdict.className}`}>
            <div className="verification-head">
              <VerdictIcon size={17} />
              <b>{verdict.label}</b>
              <code className="rule-id">{decision.ruleId}</code>
            </div>
            <p>{decision.reason}</p>
            {decision.balanceAfter !== null && (
              <p className="audit-meta">Balance after: {decision.balanceAfter}</p>
            )}
            {decision.approvalId && (
              <p className="audit-meta">Approval request: {decision.approvalId}</p>
            )}
          </div>
        )}
      </div>
    </>
  )
}
