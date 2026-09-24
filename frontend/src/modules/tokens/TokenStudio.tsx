import { useEffect, useState, type FormEvent } from 'react'
import { AlertTriangle, Brain, Loader2, Sparkles, Wand2, XCircle } from 'lucide-react'
import {
  createToken,
  interpretTokenSentence,
  previewToken,
  type TokenDraft,
  type TokenInterpretation,
  type TokenOperation,
  type TokenPreview,
} from '../../api/client'
import TokenCoin from './TokenCoin'
import TokenPromises from './TokenPromises'

const EMPTY: TokenDraft = {
  name: '',
  symbol: '',
  decimals: '0',
  initialSupply: '',
  supplyPolicy: 'FIXED',
  maxSupply: null,
  memo: null,
}

const POLICIES = [
  { value: 'FIXED', label: 'Fixed forever', hint: 'No supply key: nobody can ever mint more.' },
  { value: 'CAPPED', label: 'Capped', hint: 'The platform can mint more, never above the cap.' },
  { value: 'UNLIMITED', label: 'Unlimited', hint: 'The platform can mint more, with no cap.' },
]

const EXAMPLES = [
  'Create a loyalty token called Coffee Beans, symbol BEAN, 1 million, no decimals, fixed forever',
  'A gift card token GIFT with 2 decimals, 5000 to start, capped at 20000',
]

/**
 * Design a token, see what it will promise, then create it. The Token Agent can fill the Studio
 * from a sentence; the model only fills fields, and the Studio shows its preview like any design.
 */
export default function TokenStudio({ live, onCreated }: { live: boolean; onCreated: (op: TokenOperation) => void }) {
  const [draft, setDraft] = useState<TokenDraft>(EMPTY)
  const [preview, setPreview] = useState<TokenPreview | null>(null)
  const [previewing, setPreviewing] = useState(false)
  const [creating, setCreating] = useState(false)
  const [confirming, setConfirming] = useState(false)
  const [attemptKey, setAttemptKey] = useState(() => crypto.randomUUID())
  const [error, setError] = useState<string | null>(null)
  const [sentence, setSentence] = useState('')
  const [reading, setReading] = useState(false)
  const [readResult, setReadResult] = useState<TokenInterpretation | null>(null)

  // Live preview: the backend validates and lists the promises as the design changes.
  useEffect(() => {
    if (!draft.name && !draft.symbol && !draft.initialSupply) {
      setPreview(null)
      return
    }
    setPreviewing(true)
    const timer = setTimeout(() => {
      previewToken(draft)
        .then(setPreview)
        .catch((e) => setError(e instanceof Error ? e.message : 'Preview failed'))
        .finally(() => setPreviewing(false))
    }, 350)
    return () => clearTimeout(timer)
  }, [draft])

  function update(next: Partial<TokenDraft>) {
    setDraft((d) => ({ ...d, ...next }))
    setConfirming(false)
    setError(null)
  }

  async function read(event: FormEvent | null, text = sentence) {
    event?.preventDefault()
    setReading(true)
    setError(null)
    setReadResult(null)
    try {
      const r = await interpretTokenSentence(text)
      setReadResult(r)
      if (r.draft) {
        // Only what the sentence said replaces the Studio's fields; the rest stays for the person.
        setDraft({
          name: r.draft.name ?? '',
          symbol: r.draft.symbol ?? '',
          decimals: r.draft.decimals ?? '',
          initialSupply: r.draft.initialSupply ?? '',
          supplyPolicy: r.draft.supplyPolicy ?? '',
          maxSupply: r.draft.maxSupply ?? null,
          memo: r.draft.memo ?? null,
        })
        setAttemptKey(crypto.randomUUID())
      }
    } catch (e) {
      setError(e instanceof Error ? e.message : 'Could not read the request')
    } finally {
      setReading(false)
    }
  }

  async function create() {
    setCreating(true)
    setError(null)
    try {
      const op = await createToken(draft, attemptKey)
      onCreated(op)
      setDraft(EMPTY)
      setPreview(null)
      setConfirming(false)
      setAttemptKey(crypto.randomUUID())
    } catch (e) {
      setError(e instanceof Error ? e.message : 'The token could not be created')
    } finally {
      setCreating(false)
    }
  }

  const policy = POLICIES.find((p) => p.value === draft.supplyPolicy)
  return (
    <div className="panel token-studio">
      <form className="token-agent" onSubmit={(e) => void read(e)}>
        <p className="eyebrow">
          <Sparkles size={11} /> TOKEN AGENT
        </p>
        <div className="token-agent-row">
          <input
            maxLength={500}
            placeholder="Describe your token, e.g. a loyalty token called Coffee Beans, BEAN, 1 million, fixed forever"
            value={sentence}
            onChange={(e) => setSentence(e.target.value)}
          />
          <button type="submit" className="button primary" disabled={reading || !sentence.trim()}>
            {reading ? <Loader2 size={15} className="spin" /> : <Brain size={15} />} Fill the Studio
          </button>
        </div>
        <div className="suggestions">
          {EXAMPLES.map((s) => (
            <button
              key={s}
              type="button"
              className="suggestion"
              disabled={reading}
              onClick={() => {
                setSentence(s)
                void read(null, s)
              }}
            >
              {s}
            </button>
          ))}
        </div>
        {readResult && (
          <span className={readResult.available ? 'audit-meta' : 'problem'}>
            {readResult.source ? `${readResult.source}: ` : ''}
            {readResult.detail}
            {readResult.clarification ? ` ${readResult.clarification}` : ''}
          </span>
        )}
      </form>

      <div className="studio-grid">
        <div className="studio-form">
          <p className="eyebrow">
            <Wand2 size={11} /> TOKEN STUDIO
          </p>
          <label>
            Name
            <input value={draft.name} maxLength={100} placeholder="Coffee Beans" onChange={(e) => update({ name: e.target.value })} />
          </label>
          <div className="studio-row">
            <label>
              Symbol
              <input
                value={draft.symbol}
                maxLength={10}
                placeholder="BEAN"
                onChange={(e) => update({ symbol: e.target.value.toUpperCase() })}
              />
            </label>
            <label>
              Decimals
              <select value={draft.decimals} onChange={(e) => update({ decimals: e.target.value })}>
                <option value="">choose</option>
                {Array.from({ length: 9 }, (_, i) => (
                  <option key={i} value={String(i)}>
                    {i === 0 ? '0 (whole units)' : `${i} (0.${'0'.repeat(i - 1)}1)`}
                  </option>
                ))}
              </select>
            </label>
          </div>
          <label>
            Initial supply
            <input
              inputMode="decimal"
              value={draft.initialSupply}
              placeholder="1000000"
              onChange={(e) => update({ initialSupply: e.target.value })}
            />
          </label>
          <div className="policy-picker" role="radiogroup" aria-label="Supply">
            {POLICIES.map((p) => (
              <button
                key={p.value}
                type="button"
                role="radio"
                aria-checked={draft.supplyPolicy === p.value}
                className={draft.supplyPolicy === p.value ? 'active' : ''}
                onClick={() => update({ supplyPolicy: p.value, maxSupply: p.value === 'CAPPED' ? draft.maxSupply : null })}
              >
                {p.label}
              </button>
            ))}
          </div>
          {policy && <span className="audit-meta">{policy.hint}</span>}
          {draft.supplyPolicy === 'CAPPED' && (
            <label>
              Cap (maximum that can ever exist)
              <input
                inputMode="decimal"
                value={draft.maxSupply ?? ''}
                placeholder="5000000"
                onChange={(e) => update({ maxSupply: e.target.value })}
              />
            </label>
          )}
          <label>
            Memo <em>(optional, public)</em>
            <input
              value={draft.memo ?? ''}
              maxLength={100}
              placeholder="Loyalty points for our coffee shop"
              onChange={(e) => update({ memo: e.target.value || null })}
            />
          </label>
        </div>

        <div className="studio-preview">
          <div className="token-face">
            <TokenCoin symbol={draft.symbol || '?'} size={92} ring={preview?.valid ? 1 : null} />
            <div>
              <h3>{draft.name || 'Your token'}</h3>
              <span>
                {preview?.valid
                  ? `${preview.initialSupply} ${preview.symbol}${preview.maxSupply && preview.mintable ? ` · cap ${preview.maxSupply}` : ''}${preview.supplyType === 'INFINITE' ? ' · no cap' : ''}`
                  : 'Fill the Studio to see what it will promise'}
              </span>
              {previewing && <Loader2 size={13} className="spin" />}
            </div>
          </div>

          {preview && !preview.valid && (
            <ul className="studio-problems">
              {preview.problems.map((p) => (
                <li key={p}>
                  <XCircle size={12} /> {p}
                </li>
              ))}
            </ul>
          )}
          {preview?.warnings.map((w) => (
            <div key={w} className="audit-banner warn">
              <AlertTriangle size={15} />
              <div>
                <span>{w}</span>
              </div>
            </div>
          ))}
          {preview?.valid && <TokenPromises promises={preview.promises} planned />}
          {preview?.valid && (
            <ol className="studio-steps">
              {preview.steps.map((s) => (
                <li key={s}>{s}</li>
              ))}
            </ol>
          )}

          {preview?.valid && !confirming && (
            <button type="button" className="button primary create-token" onClick={() => setConfirming(true)}>
              <Sparkles size={15} /> Create {preview.symbol}
            </button>
          )}
          {preview?.valid && confirming && (
            <div className="confirm-create">
              <p>
                {live ? (
                  <>
                    This creates <b>{preview.name}</b> on Hedera {preview.treasury ? `with ${preview.treasury} as treasury` : ''}. Its keys
                    are permanent: the promises above can never be undone. The network fee is paid in HBAR.
                  </>
                ) : (
                  <>No Hedera credentials: this is recorded as a simulation and nothing is created.</>
                )}
              </p>
              <div>
                <button type="button" className="button secondary" onClick={() => setConfirming(false)} disabled={creating}>
                  Back
                </button>
                <button type="button" className="button primary" onClick={() => void create()} disabled={creating}>
                  {creating ? <Loader2 size={15} className="spin" /> : <Sparkles size={15} />} {live ? 'Create it on Hedera' : 'Simulate'}
                </button>
              </div>
            </div>
          )}
          {error && <div className="problem">{error}</div>}
        </div>
      </div>
    </div>
  )
}
