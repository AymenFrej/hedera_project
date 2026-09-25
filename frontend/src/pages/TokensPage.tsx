import { useCallback, useEffect, useState } from 'react'
import { Coins, Loader2, RefreshCw, ShieldAlert, ShieldCheck } from 'lucide-react'
import {
  getTokenPortfolio,
  listTokenOperations,
  type TokenOperation,
  type TokenPortfolio,
} from '../api/client'
import TokenCoin from '../modules/tokens/TokenCoin'
import TokenOperationCard from '../modules/tokens/TokenOperationCard'
import TokenPassport from '../modules/tokens/TokenPassport'
import TokenStudio from '../modules/tokens/TokenStudio'

/**
 * Tokens & Assets: design a token in the Studio (or describe it to the Token Agent), see what it
 * will promise, create it on Hedera, then read everything about it back from the ledger.
 */
export default function TokensPage() {
  const [portfolio, setPortfolio] = useState<TokenPortfolio | null>(null)
  const [operations, setOperations] = useState<TokenOperation[]>([])
  const [selected, setSelected] = useState<string | null>(null)
  const [justCreated, setJustCreated] = useState<TokenOperation | null>(null)
  const [loading, setLoading] = useState(false)
  const [error, setError] = useState<string | null>(null)

  const load = useCallback(async () => {
    setLoading(true)
    setError(null)
    try {
      const [p, ops] = await Promise.all([getTokenPortfolio(), listTokenOperations()])
      setPortfolio(p)
      setOperations(ops)
    } catch (e) {
      setError(e instanceof Error ? e.message : 'Tokens could not be loaded')
    } finally {
      setLoading(false)
    }
  }, [])

  useEffect(() => {
    void load()
  }, [load])

  const live = portfolio?.live ?? false
  return (
    <>
      <div className="page-heading">
        <div>
          <p className="eyebrow">MODULE / TOKENS</p>
          <h1>Tokens &amp; Assets</h1>
          <p className="subtitle">Design a token, see what it promises, create it on Hedera, and read it back from the ledger.</p>
        </div>
        <div className="page-actions">
          <button type="button" className="button secondary" onClick={() => void load()} disabled={loading}>
            {loading ? <Loader2 size={14} className="spin" /> : <RefreshCw size={14} />} Refresh
          </button>
        </div>
      </div>

      {portfolio && (
        <div className={`audit-banner ${live ? 'ok' : 'warn'}`}>
          {live ? <ShieldCheck size={16} /> : <ShieldAlert size={16} />}
          <div>
            <b>{live ? 'Live on Hedera' : 'Simulated: no Hedera credentials'}</b>
            <span>
              {live
                ? `Tokens are created with ${portfolio.treasury} as treasury. Every fact below is read from the Mirror Node.`
                : 'Nothing is created on Hedera. Set HEDERA_OPERATOR_ID and HEDERA_OPERATOR_PRIVATE_KEY to create real tokens.'}
            </span>
          </div>
        </div>
      )}
      {error && <div className="problem">{error}</div>}

      <TokenStudio
        live={live}
        onCreated={(op) => {
          setJustCreated(op)
          if (op.tokenId) setSelected(op.tokenId)
          void load()
        }}
      />

      {justCreated && (
        <div className="just-created">
          <TokenOperationCard op={justCreated} onSettled={() => void load()} />
        </div>
      )}

      <div className="section-label">
        TOKENS IN THE TREASURY <span className="line" />
        {portfolio?.asOf && <span>as of the ledger</span>}
      </div>
      {portfolio && live && !portfolio.mirrorAvailable && (
        <div className="problem">The Mirror Node could not be reached: the treasury's tokens cannot be shown right now.</div>
      )}
      <div className="token-grid">
        {portfolio?.tokens.map((t) => (
          <button
            key={t.tokenId}
            type="button"
            className={`token-tile ${selected === t.tokenId ? 'active' : ''}`}
            onClick={() => setSelected(selected === t.tokenId ? null : t.tokenId)}
          >
            <TokenCoin symbol={t.symbol} size={48} />
            <div>
              <b>{t.symbol ?? t.tokenId}</b>
              <span>{t.name}</span>
              <code>{t.tokenId}</code>
            </div>
            <strong>{t.balance}</strong>
            {t.createdHere && <em className="made-here">made here</em>}
          </button>
        ))}
        {portfolio && portfolio.tokens.length === 0 && (
          <div className="empty-state">
            <div className="empty-icon">
              <Coins size={18} />
            </div>
            <p>{live ? 'The treasury holds no tokens yet.' : 'No tokens: simulated mode.'}</p>
            <span>Design one in the Studio above.</span>
          </div>
        )}
      </div>

      {selected && <TokenPassport tokenId={selected} onChanged={() => void load()} />}

      {operations.length > 0 && (
        <>
          <div className="section-label">
            RECENT TOKEN OPERATIONS <span className="line" />
          </div>
          <div className="token-ops">
            {operations.slice(0, 8).map((op) => (
              <TokenOperationCard key={op.id} op={op} onSettled={() => void load()} />
            ))}
          </div>
        </>
      )}
    </>
  )
}
