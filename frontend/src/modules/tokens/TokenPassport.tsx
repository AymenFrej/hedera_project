import { useEffect, useState, type FormEvent } from 'react'
import { Link } from 'react-router-dom'
import {
  ArrowLeftRight,
  CheckCircle2,
  ExternalLink,
  Loader2,
  Lock,
  Plus,
  RefreshCw,
  UserCheck,
  XCircle,
} from 'lucide-react'
import {
  checkReceivable,
  getTokenPassport,
  mintToken,
  previewMint,
  type MintPreview,
  type Receivability,
  type TokenPassport as Passport,
} from '../../api/client'
import TokenCoin, { hueOf } from './TokenCoin'
import TokenOperationCard from './TokenOperationCard'
import TokenPromises from './TokenPromises'

/** Everything the ledger says about one token, and what can be done with it from here. */
export default function TokenPassport({ tokenId, onChanged }: { tokenId: string; onChanged: () => void }) {
  const [passport, setPassport] = useState<Passport | null>(null)
  const [error, setError] = useState<string | null>(null)
  const [loading, setLoading] = useState(false)

  async function load() {
    setLoading(true)
    setError(null)
    try {
      setPassport(await getTokenPassport(tokenId))
    } catch (e) {
      setError(e instanceof Error ? e.message : 'The token could not be read')
    } finally {
      setLoading(false)
    }
  }

  useEffect(() => {
    setPassport(null)
    void load()
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [tokenId])

  if (error) {
    return (
      <div className="panel token-passport">
        <div className="audit-banner danger">
          <XCircle size={16} />
          <div>
            <b>Could not read {tokenId}</b>
            <span>{error}</span>
          </div>
        </div>
      </div>
    )
  }
  if (!passport) {
    return (
      <div className="panel token-passport loading">
        <Loader2 size={18} className="spin" /> Reading {tokenId} from the Mirror Node…
      </div>
    )
  }

  const p = passport
  const hue = hueOf(p.symbol)
  return (
    <div className="panel token-passport">
      <div className="passport-head">
        <TokenCoin symbol={p.symbol} size={84} ring={p.treasuryShare} />
        <div>
          <p className="eyebrow">TOKEN PASSPORT · READ FROM THE LEDGER</p>
          <h3>
            {p.name} <span>{p.symbol}</span>
          </h3>
          <span className="audit-meta">
            <code>{p.tokenId}</code> · created {p.createdAt ? new Date(p.createdAt).toLocaleDateString() : 'unknown'} · treasury{' '}
            <code>{p.treasury}</code>
          </span>
        </div>
        <div className="passport-links">
          <button type="button" className="button secondary small" onClick={() => void load()} disabled={loading}>
            {loading ? <Loader2 size={12} className="spin" /> : <RefreshCw size={12} />} Refresh
          </button>
          <a className="button secondary small" href={p.tokenUrl} target="_blank" rel="noreferrer">
            HashScan <ExternalLink size={11} />
          </a>
          <Link className="button primary small" to={`/payments?asset=${encodeURIComponent(p.symbol ?? p.tokenId)}`}>
            <ArrowLeftRight size={12} /> Send with Payments
          </Link>
        </div>
      </div>

      <div className="passport-facts">
        <Fact label="Total supply" value={`${p.totalSupply} ${p.symbol}`} />
        <Fact label="Cap" value={p.maxSupply ? `${p.maxSupply} ${p.symbol}` : p.supplyType === 'INFINITE' ? 'none' : '—'} />
        <Fact label="Decimals" value={String(p.decimals)} />
        <Fact label="In the treasury" value={`${(p.treasuryShare * 100).toFixed(p.treasuryShare > 0.999 ? 0 : 1)}%`} />
        {p.memo ? <Fact label="Memo" value={p.memo} /> : null}
      </div>

      <div className="passport-holders">
        <p className="eyebrow">WHO HOLDS {p.symbol}</p>
        <div className="holders-bar" aria-label="Share of the supply per holder">
          {p.holders.map((h, i) => (
            <span
              key={h.account}
              title={`${h.account}: ${h.balance} (${(h.share * 100).toFixed(2)}%)`}
              style={{ flexGrow: Math.max(h.share, 0.004), background: `hsl(${(hue + i * 37) % 360} 65% ${h.treasury ? 62 : 48}%)` }}
            />
          ))}
        </div>
        <ul>
          {p.holders.slice(0, 6).map((h, i) => (
            <li key={h.account}>
              <i style={{ background: `hsl(${(hue + i * 37) % 360} 65% ${h.treasury ? 62 : 48}%)` }} />
              <code>{h.account}</code>
              {h.treasury && <em>treasury</em>}
              <b>{h.balance}</b>
              <span>{(h.share * 100).toFixed(2)}%</span>
            </li>
          ))}
        </ul>
        {!p.holdersComplete && <span className="audit-meta">The largest holders are shown; smaller ones are not listed.</span>}
      </div>

      <TokenPromises promises={p.promises} />

      <div className="passport-tools">
        <MintPanel passport={p} onMinted={() => { void load(); onChanged() }} />
        <ReceiveCheck tokenId={p.tokenId} symbol={p.symbol} />
      </div>

      {p.operations.length > 0 && (
        <div className="passport-history">
          <p className="eyebrow">WHAT WAS DONE HERE WITH {p.symbol}</p>
          {p.operations.map((op) => (
            <TokenOperationCard key={op.id} op={op} onSettled={() => void load()} />
          ))}
        </div>
      )}
    </div>
  )
}

function Fact({ label, value }: { label: string; value: string }) {
  return (
    <div className="fact">
      <span>{label}</span>
      <b>{value}</b>
    </div>
  )
}

function MintPanel({ passport, onMinted }: { passport: Passport; onMinted: () => void }) {
  const [amount, setAmount] = useState('')
  const [preview, setPreview] = useState<MintPreview | null>(null)
  const [busy, setBusy] = useState(false)
  const [error, setError] = useState<string | null>(null)
  const [done, setDone] = useState<string | null>(null)
  const [attemptKey, setAttemptKey] = useState(() => crypto.randomUUID())

  if (!passport.canMint) {
    return (
      <div className="tool locked">
        <p className="eyebrow">
          <Lock size={11} /> MINT MORE
        </p>
        <span>{passport.mintReason}</span>
      </div>
    )
  }

  async function check(event: FormEvent) {
    event.preventDefault()
    setBusy(true)
    setError(null)
    setDone(null)
    try {
      setPreview(await previewMint(passport.tokenId, amount))
    } catch (e) {
      setError(e instanceof Error ? e.message : 'Preview failed')
    } finally {
      setBusy(false)
    }
  }

  async function mint() {
    setBusy(true)
    setError(null)
    try {
      const op = await mintToken(passport.tokenId, amount, attemptKey)
      setDone(
        op.status === 'CONFIRMED'
          ? `Minted ${op.amount} ${op.symbol}: supply now ${op.totalSupplyAfter}`
          : `${op.status}: ${op.failureReason ?? ''}`,
      )
      setPreview(null)
      setAmount('')
      setAttemptKey(crypto.randomUUID())
      onMinted()
    } catch (e) {
      setError(e instanceof Error ? e.message : 'Mint failed')
    } finally {
      setBusy(false)
    }
  }

  return (
    <div className="tool">
      <p className="eyebrow">
        <Plus size={11} /> MINT MORE {passport.symbol}
      </p>
      <form onSubmit={(e) => void check(e)}>
        <input
          inputMode="decimal"
          placeholder="Amount"
          value={amount}
          onChange={(e) => {
            setAmount(e.target.value)
            setPreview(null)
          }}
        />
        <button type="submit" className="button secondary" disabled={busy || !amount.trim()}>
          Preview
        </button>
      </form>
      {preview && !preview.valid && preview.problems.map((p) => <div key={p} className="problem">{p}</div>)}
      {preview?.valid && (
        <div className="mint-preview">
          <span>
            Supply {preview.supplyBefore} → <b>{preview.supplyAfter} {preview.symbol}</b>
            {preview.maxSupply ? ` (cap ${preview.maxSupply})` : ''}
          </span>
          <button type="button" className="button primary" onClick={() => void mint()} disabled={busy}>
            {busy ? <Loader2 size={14} className="spin" /> : <Plus size={14} />} Mint {preview.amount} on Hedera
          </button>
        </div>
      )}
      {done && <div className="audit-meta ok-text">{done}</div>}
      {error && <div className="problem">{error}</div>}
    </div>
  )
}

function ReceiveCheck({ tokenId, symbol }: { tokenId: string; symbol: string | null }) {
  const [account, setAccount] = useState('')
  const [result, setResult] = useState<Receivability | null>(null)
  const [busy, setBusy] = useState(false)
  const [error, setError] = useState<string | null>(null)

  async function check(event: FormEvent) {
    event.preventDefault()
    setBusy(true)
    setError(null)
    setResult(null)
    try {
      setResult(await checkReceivable(tokenId, account.trim()))
    } catch (e) {
      setError(e instanceof Error ? e.message : 'Check failed')
    } finally {
      setBusy(false)
    }
  }

  return (
    <div className="tool">
      <p className="eyebrow">
        <UserCheck size={11} /> CAN THEY RECEIVE {symbol}?
      </p>
      <form onSubmit={(e) => void check(e)}>
        <input placeholder="0.0.12345" value={account} onChange={(e) => setAccount(e.target.value)} />
        <button type="submit" className="button secondary" disabled={busy || !account.trim()}>
          {busy ? <Loader2 size={13} className="spin" /> : 'Check'}
        </button>
      </form>
      {result && (
        <div className={`receive-result ${result.canReceive ? 'ok' : 'danger'}`}>
          {result.canReceive ? <CheckCircle2 size={13} /> : <XCircle size={13} />}
          <span>{result.detail}</span>
        </div>
      )}
      {error && <div className="problem">{error}</div>}
    </div>
  )
}
