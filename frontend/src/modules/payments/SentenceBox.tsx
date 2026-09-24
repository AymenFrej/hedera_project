import { useState, type FormEvent } from 'react'
import { Brain, Loader2, Sparkles, XCircle } from 'lucide-react'
import {
  interpretSentence,
  type CreatePaymentRequest,
  type SentenceInterpretation,
} from '../../api/client'
import { UnderstandingCard } from './IntentComposer'

/**
 * "What would you like to do?" in plain words. A language model on the backend reads the sentence
 * into the fields of a payment request; it never creates a transaction and never supplies an
 * account id. What it read is shown, then resolved from contacts and the Mirror Node, then
 * previewed like any other request.
 */
export default function SentenceBox({
  onPreview,
  previewing,
}: {
  onPreview: (request: CreatePaymentRequest) => void
  previewing: boolean
}) {
  const [text, setText] = useState('')
  const [result, setResult] = useState<SentenceInterpretation | null>(null)
  const [busy, setBusy] = useState(false)
  const [error, setError] = useState<string | null>(null)

  async function understand(event: FormEvent) {
    event.preventDefault()
    setBusy(true)
    setError(null)
    setResult(null)
    try {
      setResult(await interpretSentence(text))
    } catch (e) {
      setError(e instanceof Error ? e.message : 'Could not read the request')
    } finally {
      setBusy(false)
    }
  }

  const read = result?.intent
  return (
    <div className="panel intent sentence">
      <p className="eyebrow">
        <Sparkles size={11} /> PAYMENT AGENT
      </p>
      <h3>What would you like to do?</h3>
      <form onSubmit={(e) => void understand(e)}>
        <textarea
          required
          maxLength={500}
          rows={2}
          placeholder="Pay Zied 5 PAYTEST from essentials, but keep at least 10 in my account."
          value={text}
          onChange={(e) => {
            setText(e.target.value)
            setResult(null)
          }}
        />
        <button type="submit" className="button primary" disabled={busy || !text.trim()}>
          {busy ? <Loader2 size={15} className="spin" /> : <Brain size={15} />} Understand request
        </button>
      </form>
      <span className="audit-meta">
        The language model only reads your sentence into a request. It cannot send anything: names
        are resolved from your contacts, then the payment is previewed, checked by the policy and
        audited like any other.
      </span>

      {error && (
        <div className="audit-banner danger">
          <XCircle size={16} />
          <div>
            <b>Request failed</b>
            <span>{error}</span>
          </div>
        </div>
      )}

      {result && !result.available && (
        <div className="audit-banner warn">
          <XCircle size={16} />
          <div>
            <b>Language model not configured</b>
            <span>{result.detail}</span>
          </div>
        </div>
      )}

      {result && result.available && (
        <>
          <div className="model-read">
            <p className="eyebrow">WHAT {(result.source ?? 'THE MODEL').toUpperCase()} READ</p>
            {read ? (
              <dl>
                <dt>Recipient</dt>
                <dd>{read.recipient ?? '—'}</dd>
                <dt>Amount</dt>
                <dd>{read.amount ?? '—'}</dd>
                <dt>Asset</dt>
                <dd>{read.asset ?? 'HBAR'}</dd>
                <dt>Envelope</dt>
                <dd>{read.envelope ?? '—'}</dd>
                {read.keepAtLeast && (
                  <>
                    <dt>Keep at least</dt>
                    <dd>{read.keepAtLeast}</dd>
                  </>
                )}
              </dl>
            ) : null}
            <span className="audit-meta">{result.detail}</span>
            {result.clarification && <span className="problem">{result.clarification}</span>}
          </div>
          {result.understanding && (
            <UnderstandingCard
              understanding={result.understanding}
              previewing={previewing}
              onPreview={onPreview}
            />
          )}
        </>
      )}
    </div>
  )
}
