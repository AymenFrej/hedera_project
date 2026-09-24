import { CheckCircle2, CircleDashed, CircleHelp, MinusCircle, XCircle } from 'lucide-react'
import type { SafetyRow } from '../../api/client'

const ICON = {
  PASS: <CheckCircle2 size={13} />,
  FAIL: <XCircle size={13} />,
  WAITING: <CircleDashed size={13} />,
  NOT_APPLICABLE: <MinusCircle size={13} />,
  UNKNOWN: <CircleHelp size={13} />,
}

/**
 * Transaction safety, row by row. Each row is a fact the backend holds or just checked; a row it
 * cannot back says so (— or ?) instead of showing a tick. Not a score: nothing is added up.
 */
export default function SafetySummary({ rows }: { rows: SafetyRow[] }) {
  return (
    <div className="safety">
      <p className="eyebrow">TRANSACTION SAFETY</p>
      <ul>
        {rows.map((r) => (
          <li key={r.name} className={r.state.toLowerCase()}>
            {ICON[r.state]}
            <b>{r.name}</b>
            <span>{r.detail}</span>
          </li>
        ))}
      </ul>
    </div>
  )
}
