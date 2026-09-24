import { useEffect, useState } from 'react'
import { Link } from 'react-router-dom'
import { decideSpend, getPolicyRules, getPolicyState, resetDemo, type DecideResponse, type PolicyRule, type PolicyStateResponse } from '../api/client'

export default function PoliciesPage() {
  const [state, setState] = useState<PolicyStateResponse | null>(null)
  const [rules, setRules] = useState<PolicyRule[]>([])
  const [envelope, setEnvelope] = useState('RENT')
  const [amount, setAmount] = useState('400')
  const [counterparty, setCounterparty] = useState('landlord-tunis')
  const [decision, setDecision] = useState<DecideResponse | null>(null)
  const [busy, setBusy] = useState(false)
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState<string | null>(null)

  async function refresh() {
    setLoading(true); setError(null)
    try {
      const [nextState, nextRules] = await Promise.all([getPolicyState(), getPolicyRules()])
      setState(nextState); setRules(nextRules)
    } catch (e) { setError(e instanceof Error ? e.message : 'Could not load policies') }
    finally { setLoading(false) }
  }
  useEffect(() => { void refresh() }, [])

  async function decide() {
    if (!Number.isSafeInteger(Number(amount)) || Number(amount) <= 0 || !counterparty.trim()) {
      setError('Enter a positive whole-number amount and a counterparty.'); return
    }
    setBusy(true); setError(null); setDecision(null)
    try {
      const answer = await decideSpend({ envelope, amount: Number(amount), counterparty: counterparty.trim() })
      setDecision(answer)
      setState(current => current ? { ...current, envelopes: answer.envelopes } : current)
    } catch (e) { setError(e instanceof Error ? e.message : 'Could not evaluate request') }
    finally { setBusy(false) }
  }
  async function reset() {
    if (!window.confirm('Reset shared demo budgets and remove ALL approval requests for every user? Existing audit events will remain.')) return
    setBusy(true); setError(null)
    try { setState(await resetDemo()); setDecision(null) }
    catch (e) { setError(e instanceof Error ? e.message : 'Could not reset demo') }
    finally { setBusy(false) }
  }
  const disabled = busy || loading || !state
  return <>
    <div className="page-heading"><div><p className="eyebrow">MODULE / POLICIES</p><h1>Policies &amp; Approvals</h1>
      <p className="subtitle">Real rule engine, shared demo budgets. These are not wallet balances and no HBAR or token transfer is performed. Audit records may be anchored on Hedera.</p>
    </div><button className="button secondary" disabled={busy || loading} onClick={() => void refresh()}>Refresh</button></div>
    {error && <div className="audit-banner danger" role="alert">{error}</div>}
    {loading && <p role="status">Loading policy state and rulebook…</p>}
    <div className="envelope-grid">{state && Object.entries(state.envelopes).map(([name, balance]) => <div className="envelope-card" key={name}><span>{name}</span><strong>{balance}</strong><span>Remaining demo budget</span></div>)}</div>
    <section className="data-panel">
      <form className="decide-form" onSubmit={e => { e.preventDefault(); void decide() }}>
        <label>Envelope<select value={envelope} onChange={e => setEnvelope(e.target.value)}>{['RENT','ESSENTIALS','EMERGENCY'].map(name => <option key={name}>{name}</option>)}</select></label>
        <label>Amount<input type="number" required min="1" step="1" value={amount} onChange={e => setAmount(e.target.value)}/></label>
        <label>Counterparty<input required value={counterparty} onChange={e => setCounterparty(e.target.value)}/></label>
        <button className="button primary" disabled={disabled}>{busy ? 'Working…' : 'Evaluate request'}</button>
        <button type="button" className="button secondary" disabled={disabled} onClick={() => void reset()}>Reset shared demo</button>
      </form>
      {state && <p className="feature-note">Known counterparties: {state.knownCounterparties.join(', ') || 'None'}</p>}
      {decision && <div className={`verdict-card ${decision.verdict === 'ALLOW' ? 'ok' : decision.verdict === 'HOLD' ? 'warn' : 'danger'}`} role="status">
        <h2>{decision.verdict}</h2><code>{decision.ruleId}</code><p>{decision.reason}</p>
        {decision.balanceAfter !== null && <p>Remaining demo budget: {decision.balanceAfter}</p>}
        {decision.approvalId && <p><Link to={`/approvals?request=${encodeURIComponent(decision.approvalId)}`}>Review approval request</Link></p>}
        {decision.auditEventId && <p><Link to={`/audit?event=${encodeURIComponent(decision.auditEventId)}`}>View audit event</Link> — {decision.anchored ? 'Anchored on Hedera' : 'Not yet anchored'}</p>}
      </div>}
    </section>
    <section className="feature-section"><h2>Active rulebook</h2><p>Read-only rules supplied by the backend. Editing rules is not supported yet.</p>
      <div className="table-scroll"><table className="feature-table"><caption>Policy rules and outcomes</caption><thead><tr><th>Rule</th><th>Verdict</th><th>Reason</th></tr></thead>
        <tbody>{rules.map(rule => <tr key={rule.ruleId}><td><code>{rule.ruleId}</code></td><td>{rule.verdict}</td><td>{rule.reason}</td></tr>)}</tbody></table></div>
      {!loading && !rules.length && <p>{error ? 'Rulebook unavailable. Use Refresh to retry.' : 'No rules returned.'}</p>}
    </section>
  </>
}
