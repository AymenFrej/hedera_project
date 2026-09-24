import { KeyRound, ShieldCheck } from 'lucide-react'
import type { TokenPromise } from '../../api/client'

/**
 * What a token can never do, and what someone can do with it. The backend derives every line from
 * the token's keys (planned ones in the Studio, the ledger's in the Passport); nothing is hand-written.
 */
export default function TokenPromises({ promises, planned }: { promises: TokenPromise[]; planned?: boolean }) {
  const guarantees = promises.filter((p) => p.kind === 'GUARANTEE')
  const powers = promises.filter((p) => p.kind === 'POWER')
  return (
    <div className="token-promises">
      <div>
        <p className="eyebrow">
          <ShieldCheck size={11} /> {planned ? 'IT WILL NEVER' : 'GUARANTEED BY ITS KEYS'}
        </p>
        <ul>
          {guarantees.map((p) => (
            <li key={p.title} className="guarantee">
              <ShieldCheck size={14} />
              <div>
                <b>{p.title}</b>
                <span>{p.detail}</span>
              </div>
            </li>
          ))}
          {guarantees.length === 0 && <li className="audit-meta">No guarantees: every power below applies.</li>}
        </ul>
      </div>
      <div>
        <p className="eyebrow">
          <KeyRound size={11} /> {planned ? 'POWERS IT WILL KEEP' : 'POWERS SOMEONE HOLDS'}
        </p>
        <ul>
          {powers.map((p) => (
            <li key={p.title} className="power">
              <KeyRound size={14} />
              <div>
                <b>{p.title}</b>
                <span>{p.detail}</span>
              </div>
            </li>
          ))}
          {powers.length === 0 && <li className="audit-meta">None: nobody holds any power over this token.</li>}
        </ul>
      </div>
    </div>
  )
}
