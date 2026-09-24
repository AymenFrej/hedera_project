import { useRef, useState, type FormEvent } from 'react'
import { AlertTriangle, Brain, FileText, Loader2, Paperclip, Sparkles, X, XCircle } from 'lucide-react'
import {
  interpretDocument,
  interpretSentence,
  understandIntent,
  type Contact,
  type CreatePaymentRequest,
  type SentenceInterpretation,
} from '../../api/client'
import { UnderstandingCard } from './IntentComposer'

const MAX_DOCUMENT_BYTES = 5 * 1024 * 1024
const ACCEPT = '.pdf,.png,.jpg,.jpeg,.webp,.txt,.csv,.md'
const TYPE_BY_EXTENSION: Record<string, string> = {
  pdf: 'application/pdf',
  png: 'image/png',
  jpg: 'image/jpeg',
  jpeg: 'image/jpeg',
  webp: 'image/webp',
  txt: 'text/plain',
  csv: 'text/csv',
  md: 'text/markdown',
}

/** The browser leaves the type empty for some files (.md, .csv): fall back to the extension. */
function typeOf(file: File): string {
  const extension = file.name.split('.').pop()?.toLowerCase() ?? ''
  return TYPE_BY_EXTENSION[extension] ?? file.type
}

function base64Of(file: File): Promise<string> {
  return new Promise((resolve, reject) => {
    const reader = new FileReader()
    reader.onload = () => resolve(String(reader.result).split(',', 2)[1] ?? '')
    reader.onerror = () => reject(new Error('The file could not be read'))
    reader.readAsDataURL(file)
  })
}

/**
 * Suggestions built only from what exists: your contacts and the assets on the paying account.
 * Clicking one only reads it; nothing is paid before preview and confirmation.
 */
function suggestionsFor(contacts: Contact[], tokenSymbols: string[]): string[] {
  const [first, second] = contacts
  if (!first) return []
  const list = [`Pay ${first.name} 1 HBAR from essentials`]
  if (second) list.push(`Pay ${second.name} 2 HBAR from rent`)
  const token = tokenSymbols.find((s) => s && s !== 'HBAR')
  if (token) list.push(`Send 2 ${token} to ${first.name}, but keep at least 100 ${token}`)
  return list
}

/**
 * "What would you like to do?" in plain words, or an attached invoice. A language model on the
 * backend reads it into the fields of a payment request; it never creates a transaction and never
 * supplies an account id. What it read is shown, then resolved from contacts and the Mirror Node,
 * then previewed like any other request.
 */
export default function SentenceBox({
  contacts,
  tokenSymbols,
  onPreview,
  previewing,
}: {
  contacts: Contact[]
  tokenSymbols: string[]
  onPreview: (request: CreatePaymentRequest) => void
  previewing: boolean
}) {
  const [text, setText] = useState('')
  const [file, setFile] = useState<File | null>(null)
  const [result, setResult] = useState<SentenceInterpretation | null>(null)
  const [busy, setBusy] = useState(false)
  const [error, setError] = useState<string | null>(null)
  const picker = useRef<HTMLInputElement>(null)

  async function run(sentence: string, attached: File | null) {
    setBusy(true)
    setError(null)
    setResult(null)
    try {
      if (attached) {
        setResult(
          await interpretDocument(attached.name, typeOf(attached), await base64Of(attached), sentence),
        )
      } else {
        setResult(await interpretSentence(sentence))
      }
    } catch (e) {
      setError(e instanceof Error ? e.message : 'Could not read the request')
    } finally {
      setBusy(false)
    }
  }

  /** The model's reading stays as it was; only the envelope the person picked is added. */
  async function chooseEnvelope(envelope: string) {
    if (!result?.intent) return
    const intent = { ...result.intent, envelope }
    setBusy(true)
    setError(null)
    try {
      const understanding = await understandIntent(intent)
      setResult({ ...result, intent, understanding })
    } catch (e) {
      setError(e instanceof Error ? e.message : 'Could not read the request')
    } finally {
      setBusy(false)
    }
  }

  function understand(event: FormEvent) {
    event.preventDefault()
    void run(text, file)
  }

  function suggest(sentence: string) {
    setText(sentence)
    setFile(null)
    void run(sentence, null)
  }

  function attach(chosen: File | undefined) {
    if (picker.current) picker.current.value = ''
    if (!chosen) return
    setResult(null)
    if (!TYPE_BY_EXTENSION[chosen.name.split('.').pop()?.toLowerCase() ?? '']) {
      setError('Attach a PDF, an image (PNG, JPEG, WebP) or a text file')
      return
    }
    if (chosen.size > MAX_DOCUMENT_BYTES) {
      setError('The document is larger than 5 MB')
      return
    }
    setError(null)
    setFile(chosen)
  }

  const suggestions = suggestionsFor(contacts, tokenSymbols)
  const read = result?.intent
  return (
    <div className="panel intent sentence">
      <p className="eyebrow">
        <Sparkles size={11} /> PAYMENT AGENT
      </p>
      <h3>What would you like to do?</h3>
      <form onSubmit={understand}>
        <div className="sentence-input">
          <textarea
            required={!file}
            maxLength={500}
            rows={2}
            placeholder={
              file
                ? 'Optional note, e.g. "pay it from rent"'
                : 'Pay Zied 5 PAYTEST from essentials, but keep at least 10 in my account.'
            }
            value={text}
            onChange={(e) => {
              setText(e.target.value)
              setResult(null)
            }}
          />
          {file && (
            <span className="attached-file">
              <FileText size={13} /> {file.name}
              <button
                type="button"
                className="text-button"
                aria-label="Remove the document"
                onClick={() => {
                  setFile(null)
                  setResult(null)
                }}
              >
                <X size={12} />
              </button>
            </span>
          )}
        </div>
        <div className="sentence-actions">
          <input
            ref={picker}
            type="file"
            accept={ACCEPT}
            hidden
            onChange={(e) => attach(e.target.files?.[0])}
          />
          <button
            type="button"
            className="button secondary"
            onClick={() => picker.current?.click()}
            disabled={busy}
            title="Attach an invoice or a bill (PDF, image or text, up to 5 MB)"
          >
            <Paperclip size={15} /> Attach
          </button>
          <button type="submit" className="button primary" disabled={busy || (!file && !text.trim())}>
            {busy ? <Loader2 size={15} className="spin" /> : <Brain size={15} />}{' '}
            {file ? 'Read document' : 'Understand request'}
          </button>
        </div>
      </form>

      {suggestions.length > 0 ? (
        <div className="suggestions">
          {suggestions.map((s) => (
            <button key={s} type="button" className="suggestion" disabled={busy} onClick={() => suggest(s)}>
              {s}
            </button>
          ))}
        </div>
      ) : (
        <span className="audit-meta">Add a contact under Contacts to get suggestions here.</span>
      )}

      <span className="audit-meta">
        The language model only reads your sentence or document into a request. It cannot send
        anything: names are resolved from your contacts, then the payment is previewed, checked by
        the policy and audited like any other.
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
            <p className="eyebrow">
              WHAT {(result.source ?? 'THE MODEL').toUpperCase()} READ
              {result.document ? ` FROM ${result.document.toUpperCase()}` : ''}
            </p>
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
                {read.memo && (
                  <>
                    <dt>Memo</dt>
                    <dd>{read.memo}</dd>
                  </>
                )}
              </dl>
            ) : null}
            <span className="audit-meta">{result.detail}</span>
            {result.clarification && <span className="problem">{result.clarification}</span>}
          </div>
          {result.warning && (
            <div className="audit-banner warn">
              <AlertTriangle size={16} />
              <div>
                <b>Check before you confirm</b>
                <span>{result.warning}</span>
              </div>
            </div>
          )}
          {result.understanding && (
            <UnderstandingCard
              understanding={result.understanding}
              previewing={previewing}
              onPreview={onPreview}
              onChooseEnvelope={(e) => void chooseEnvelope(e)}
            />
          )}
        </>
      )}
    </div>
  )
}
