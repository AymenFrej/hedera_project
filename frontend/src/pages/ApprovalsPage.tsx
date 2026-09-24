import { useEffect, useState } from 'react'
import { CheckCircle2, FileCheck2, XCircle } from 'lucide-react'
import { answerApproval, listApprovals, type ApprovalRequest } from '../api/client'

export default function ApprovalsPage() {
  const [items, setItems] = useState<ApprovalRequest[]>([])
  const [error, setError] = useState<string | null>(null)
  const [busy, setBusy] = useState<string | null>(null)

  function load() {
    listApprovals()
      .then(setItems)
      .catch(e => setError(e instanceof Error ? e.message : 'Backend unreachable'))
  }

  useEffect(load, [])

  async function answer(id: string, verdict: 'approve' | 'reject') {
    setBusy(id)
    setError(null)
    try {
      const updated = await answerApproval(id, verdict)
      setItems(current => current.map(item => (item.id === id ? updated : item)))
    } catch (e) {
      setError(e instanceof Error ? e.message : 'Backend unreachable')
    } finally {
      setBusy(null)
    }
  }

  return (
    <>
      <div className="page-heading">
        <div>
          <p className="eyebrow">MODULE / APPROVALS</p>
          <h1>Approvals</h1>
          <p className="subtitle">
            Every HOLD verdict waits here. A denial never reaches this queue — what cannot settle is
            never offered to a human.
          </p>
        </div>
      </div>

      {error && (
        <div className="audit-banner danger">
          <XCircle size={16} />
          <div>
            <b>Could not reach the approvals API</b>
            <span>{error}</span>
          </div>
        </div>
      )}

      <div className="data-panel">
        {items.length ? (
          <div className="data-list">
            {items.map(item => (
              <div className="data-row" key={item.id}>
                <div className="data-leading">
                  <div className="row-icon">
                    <FileCheck2 size={16} />
                  </div>
                  <div>
                    <b>
                      {item.amount} to {item.counterparty} ({item.envelope})
                    </b>
                    <span>{item.reason}</span>
                  </div>
                </div>
                <div className="row-meta">
                  <code className="rule-id">{item.ruleId}</code>
                  {item.status === 'PENDING' ? (
                    <>
                      <button
                        className="button secondary"
                        disabled={busy === item.id}
                        onClick={() => answer(item.id, 'reject')}
                      >
                        <XCircle size={14} />
                        Reject
                      </button>
                      <button
                        className="button primary"
                        disabled={busy === item.id}
                        onClick={() => answer(item.id, 'approve')}
                      >
                        <CheckCircle2 size={14} />
                        Approve
                      </button>
                    </>
                  ) : (
                    <span className="status-badge">
                      {item.status} by {item.decidedBy}
                    </span>
                  )}
                </div>
              </div>
            ))}
          </div>
        ) : (
          <div className="empty-state">
            <div className="empty-icon">
              <FileCheck2 size={20} />
            </div>
            <p>Nothing waiting for a human.</p>
            <span>Submit an EMERGENCY spend on the Policies page to raise one.</span>
          </div>
        )}
      </div>
    </>
  )
}
