import { useEffect, useState } from 'react'
import { Link, useSearchParams } from 'react-router-dom'
import { answerApproval, listApprovals, type ApprovalRequest } from '../api/client'

export default function ApprovalsPage() {
  const [items, setItems] = useState<ApprovalRequest[]>([])
  const [error, setError] = useState<string | null>(null)
  const [busy, setBusy] = useState(false)
  const [loading, setLoading] = useState(true)
  const [filter, setFilter] = useState('ALL')
  const [params, setParams] = useSearchParams()
  const selected = params.get('request')
  async function load() {
    setLoading(true); setError(null)
    try { setItems(await listApprovals()) }
    catch (e) { setError(e instanceof Error ? e.message : 'Could not load approvals') }
    finally { setLoading(false) }
  }
  useEffect(() => { void load() }, [])
  async function answer(item: ApprovalRequest, verdict: 'approve' | 'reject') {
    if (!window.confirm(`${verdict === 'approve' ? 'Approve' : 'Reject'} ${item.amount} for ${item.counterparty}? Approval debits the shared demo budget, not a Hedera wallet.`)) return
    setBusy(true); setError(null)
    try {
      const updated = await answerApproval(item.id, verdict)
      setItems(current => current.map(row => row.id === item.id ? updated : row))
    } catch (e) {
      const message = e instanceof Error ? e.message : 'Could not settle approval'
      try { setItems(await listApprovals()) } catch { /* Keep the original action error. */ }
      setError(message)
    } finally { setBusy(false) }
  }
  const visible = items.filter(item => (!selected || item.id === selected) && (filter === 'ALL' || item.status === filter))
  return <>
    <div className="page-heading"><div><p className="eyebrow">MODULE / APPROVALS</p><h1>Approvals</h1><p className="subtitle">Review held policy requests and decision history. Approving affects demo budgets only; it does not send a payment.</p></div>
      <button className="button secondary" disabled={busy || loading} onClick={() => void load()}>Refresh</button></div>
    {error && <div className="audit-banner danger" role="alert">{error}</div>}
    <div className="feature-toolbar"><label>Status <select value={filter} onChange={e => setFilter(e.target.value)}>{['ALL','PENDING','APPROVED','REJECTED'].map(value => <option key={value}>{value}</option>)}</select></label>
      {selected && <button className="button secondary" onClick={() => setParams({})}>Show all requests</button>}<span>{visible.length} requests</span></div>
    <section className="data-panel" aria-busy={loading || busy}>
      {loading ? <p className="feature-note" role="status">Loading approvals…</p> : visible.length ? visible.map(item => <article className="approval-item" key={item.id}>
        <div><h2>{item.amount ?? '—'} to {item.counterparty ?? 'Unknown recipient'}</h2><span className="status-badge">{item.status}</span></div>
        <p>{item.reason}</p><p>Envelope: {item.envelope ?? '—'} · Rule: <code>{item.ruleId}</code></p>
        <p className="audit-meta">Request: {item.id}<br/>Requested: {item.requestedAt ? new Date(item.requestedAt).toLocaleString() : 'Unknown'}</p>
        {item.decidedAt && <p>Decided {new Date(item.decidedAt).toLocaleString()} by {item.decidedBy ?? 'Unknown actor'}</p>}
        <div className="page-actions">{item.taskId && <Link to={`/audit?event=${encodeURIComponent(item.taskId)}`}>View originating audit event</Link>}
          {item.status === 'PENDING' && <><button className="button secondary" disabled={busy} onClick={() => void answer(item, 'reject')}>Reject</button><button className="button primary" disabled={busy} onClick={() => void answer(item, 'approve')}>Approve</button></>}
        </div>
      </article>) : <div className="empty-state"><p>{error ? 'Approvals unavailable. Refresh to retry.' : 'No matching requests.'}</p><Link to="/policies">Open Policies</Link></div>}
    </section>
  </>
}
