import { ShieldAlert, ShieldCheck, ShieldQuestion } from 'lucide-react'
import type { PolicyExplanation } from '../../api/client'

/**
 * Why the policy decided what it decided, with the numbers it decided on. Renders the backend's
 * explanation as is: the verdict, rule and reason are the policy's, the amounts are the envelope's
 * at decision time. Nothing here is computed or invented in the browser.
 */
export default function PolicyProtection({
  why,
  asset,
  whenNotSent,
}: {
  why: PolicyExplanation
  /** How to write the asset after an amount, e.g. "ℏ" or "PAYTEST". */
  asset: string
  /** Shown when no Hedera transaction exists: "was not created" or "will not be created". */
  whenNotSent?: string
}) {
  const denied = why.verdict === 'DENY'
  const held = why.verdict === 'HOLD'
  const Icon = denied ? ShieldAlert : held ? ShieldQuestion : ShieldCheck
  return (
    <div className={`protection ${denied ? 'danger' : held ? 'warn' : 'ok'}`}>
      <div className="protection-head">
        <Icon size={16} />
        <b>
          {denied
            ? 'Payment protection: blocked before reaching Hedera'
            : held
              ? 'Payment protection: held for a human'
              : 'Payment protection: allowed'}
        </b>
      </div>
      <dl>
        <dt>Requested</dt>
        <dd>
          {why.requested} {asset}
        </dd>
        {why.envelope && (
          <>
            <dt>{`In ${why.envelope.toLowerCase()} at decision`}</dt>
            <dd>{why.available !== null ? `${why.available} ${asset}` : 'not funded'}</dd>
          </>
        )}
        {why.shortfall && (
          <>
            <dt>Short by</dt>
            <dd className="short">
              {why.shortfall} {asset}
            </dd>
          </>
        )}
        <dt>Rule</dt>
        <dd>
          <code>{why.ruleId}</code>
        </dd>
        <dt>Reason</dt>
        <dd>{why.reason}</dd>
        {!why.transactionCreated && whenNotSent && (
          <>
            <dt>Hedera transaction</dt>
            <dd className="not-created">{whenNotSent}</dd>
          </>
        )}
      </dl>
    </div>
  )
}
