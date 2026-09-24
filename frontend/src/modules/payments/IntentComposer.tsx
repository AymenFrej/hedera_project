import { useState, type FormEvent } from 'react'
import { BookUser, Brain, CheckCircle2, Eye, Loader2, Trash2, UserPlus, XCircle } from 'lucide-react'
import {
  addContact,
  deleteContact,
  understandIntent,
  type Contact,
  type CreatePaymentRequest,
  type IntentUnderstanding,
} from '../../api/client'

const EMPTY = { recipient: '', amount: '', asset: '', envelope: 'essentials', keepAtLeast: '' }

/**
 * "What do you want to pay?" as a sentence with blanks. Each blank is a real field of the payment
 * intent, the same contract the future orchestrator will produce from free text; nothing is parsed
 * or guessed. "Understand" resolves each field on the backend and shows where each value came from.
 */
export default function IntentComposer({
  contacts,
  assetSymbols,
  onContactsChanged,
  onPreview,
  previewing,
}: {
  contacts: Contact[]
  assetSymbols: string[]
  onContactsChanged: () => void
  onPreview: (request: CreatePaymentRequest) => void
  previewing: boolean
}) {
  const [intent, setIntent] = useState(EMPTY)
  const [understanding, setUnderstanding] = useState<IntentUnderstanding | null>(null)
  const [busy, setBusy] = useState(false)
  const [error, setError] = useState<string | null>(null)
  const [showContacts, setShowContacts] = useState(false)

  function update(next: typeof intent) {
    setIntent(next)
    setUnderstanding(null)
  }

  async function understand(event: FormEvent | null, fields = intent) {
    event?.preventDefault()
    setBusy(true)
    setError(null)
    try {
      setUnderstanding(
        await understandIntent({
          recipient: fields.recipient,
          amount: fields.amount,
          asset: fields.asset || 'HBAR',
          envelope: fields.envelope || null,
          memo: null,
          keepAtLeast: fields.keepAtLeast || null,
        }),
      )
    } catch (e) {
      setError(e instanceof Error ? e.message : 'Could not understand the request')
    } finally {
      setBusy(false)
    }
  }

  return (
    <div className="panel intent">
      <div className="intent-head">
        <div>
          <p className="eyebrow">
            <Brain size={11} /> PAYMENT REQUEST
          </p>
          <h3>Manual request</h3>
        </div>
        <button type="button" className="text-button" onClick={() => setShowContacts((v) => !v)}>
          <BookUser size={13} /> Contacts ({contacts.length})
        </button>
      </div>

      <form className="intent-sentence" onSubmit={(e) => void understand(e)}>
        <span>Pay</span>
        <input
          required
          list="payment-contacts"
          placeholder="Zied or 0.0.12345"
          value={intent.recipient}
          onChange={(e) => update({ ...intent, recipient: e.target.value })}
        />
        <input
          required
          className="short"
          inputMode="decimal"
          placeholder="5"
          value={intent.amount}
          onChange={(e) => update({ ...intent, amount: e.target.value })}
        />
        <input
          className="short"
          list="payment-assets"
          placeholder="HBAR"
          value={intent.asset}
          onChange={(e) => update({ ...intent, asset: e.target.value })}
        />
        <span>from</span>
        <select value={intent.envelope} onChange={(e) => update({ ...intent, envelope: e.target.value })}>
          <option value="rent">rent</option>
          <option value="essentials">essentials</option>
          <option value="emergency">emergency</option>
        </select>
        <span>keeping at least</span>
        <input
          className="short"
          inputMode="decimal"
          placeholder="optional"
          value={intent.keepAtLeast}
          onChange={(e) => update({ ...intent, keepAtLeast: e.target.value })}
        />
        <button type="submit" className="button primary" disabled={busy}>
          {busy ? <Loader2 size={15} className="spin" /> : <Brain size={15} />} Understand
        </button>
        <datalist id="payment-contacts">
          {contacts.map((c) => (
            <option key={c.id} value={c.name} />
          ))}
        </datalist>
        <datalist id="payment-assets">
          {assetSymbols.map((s) => (
            <option key={s} value={s} />
          ))}
        </datalist>
      </form>
      <span className="audit-meta">
        Each blank is a field of the same request the sentence box produces. Use it when no language
        model is configured, or to fill a request field by field.
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

      {understanding && (
        <UnderstandingCard
          understanding={understanding}
          previewing={previewing}
          onPreview={onPreview}
          onChooseEnvelope={(envelope) => {
            const next = { ...intent, envelope }
            setIntent(next)
            void understand(null, next)
          }}
        />
      )}

      {showContacts && <ContactBook contacts={contacts} onChanged={onContactsChanged} />}
    </div>
  )
}

/** Each field of the request, what it resolved to and from where; then Preview when complete. */
export function UnderstandingCard({
  understanding,
  previewing,
  onPreview,
  onChooseEnvelope,
}: {
  understanding: IntentUnderstanding
  previewing: boolean
  onPreview: (request: CreatePaymentRequest) => void
  /** Re-reads the request with the envelope the person picked. */
  onChooseEnvelope?: (envelope: string) => void
}) {
  return (
    <div className={`understanding ${understanding.understood ? 'ok' : 'danger'}`}>
      <p className="eyebrow">UNDERSTANDING YOUR REQUEST</p>
      <ul>
        {understanding.steps.map((s) => (
          <li key={s.field}>
            {s.value ? <CheckCircle2 size={13} /> : <XCircle size={13} />}
            <b>{s.field}</b>
            <span>
              {s.input} → {s.value ?? 'not resolved'}
            </span>
            <em>{s.source}</em>
          </li>
        ))}
      </ul>
      {understanding.problems.map((p) => (
        <div key={p} className="problem">
          {p}
        </div>
      ))}
      {onChooseEnvelope && understanding.envelopeChoices?.length > 0 && (
        <div className="envelope-choice">
          <span>Which envelope pays?</span>
          {understanding.envelopeChoices.map((e) => (
            <button key={e} type="button" className="suggestion" onClick={() => onChooseEnvelope(e)}>
              {e}
            </button>
          ))}
        </div>
      )}
      {understanding.understood && understanding.request && (
        <button
          type="button"
          className="button primary"
          disabled={previewing}
          onClick={() => understanding.request && onPreview(understanding.request)}
        >
          {previewing ? <Loader2 size={15} className="spin" /> : <Eye size={15} />} Preview
          transaction
        </button>
      )}
    </div>
  )
}

function ContactBook({ contacts, onChanged }: { contacts: Contact[]; onChanged: () => void }) {
  const [name, setName] = useState('')
  const [accountId, setAccountId] = useState('')
  const [error, setError] = useState<string | null>(null)

  async function add(event: FormEvent) {
    event.preventDefault()
    setError(null)
    try {
      await addContact(name.trim(), accountId.trim())
      setName('')
      setAccountId('')
      onChanged()
    } catch (e) {
      setError(e instanceof Error ? e.message : 'Could not add the contact')
    }
  }

  async function remove(id: string) {
    await deleteContact(id)
    onChanged()
  }

  return (
    <div className="contact-book">
      <p className="eyebrow">CONTACTS · NAME → HEDERA ACCOUNT</p>
      <ul>
        {contacts.map((c) => (
          <li key={c.id}>
            <b>{c.name}</b>
            <code>{c.accountId}</code>
            <button type="button" className="text-button" onClick={() => void remove(c.id)}>
              <Trash2 size={12} />
            </button>
          </li>
        ))}
        {contacts.length === 0 && <li className="audit-meta">No contacts yet.</li>}
      </ul>
      <form onSubmit={(e) => void add(e)}>
        <input required placeholder="Name" value={name} onChange={(e) => setName(e.target.value)} />
        <input
          required
          placeholder="0.0.12345"
          value={accountId}
          onChange={(e) => setAccountId(e.target.value)}
        />
        <button type="submit" className="button secondary small">
          <UserPlus size={13} /> Add
        </button>
      </form>
      {error && <span className="problem">{error}</span>}
    </div>
  )
}
