import { useEffect, useState } from 'react'
import { Link } from 'react-router-dom'
import { getCurrentAccount } from '../api/client'
export default function AccountsPage() {
  const [account, setAccount] = useState<Awaited<ReturnType<typeof getCurrentAccount>>>()
  const [error, setError] = useState('')
  const [busy, setBusy] = useState(false)
  const [copied, setCopied] = useState(false)
  async function load() { setBusy(true); setCopied(false); setError(''); try { setAccount(await getCurrentAccount()) } catch(e) { setError((e as Error).message) } finally { setBusy(false) } }
  useEffect(() => { void load() }, [])
  return <section className="settings-card data-panel"><h1>My wallet</h1>
    {error && <p role="alert">{error}</p>}
    {account && <><h2>Hedera account ID</h2><code>{account.hederaAccountId}</code>
      <p>Status: {account.status}</p><p>Recorded balance: {account.balance} HBAR (not a live balance query)</p>
      {account.hederaAccountId.includes('mock') || account.hederaAccountId.includes('demo') ? <p>This is a demo wallet.</p> : <p>Your wallet signing key is kept on the server.</p>}
      <button className="button secondary" onClick={async () => { try { await navigator.clipboard.writeText(account.hederaAccountId); setCopied(true) } catch { setError('Copy failed. Select the account ID to copy it manually.') } }}>{copied ? 'Copied' : 'Copy wallet ID'}</button>
    </>}
    <button className="button secondary" disabled={busy} onClick={() => void load()}>{busy ? 'Loading…' : 'Refresh wallet'}</button>
    <Link to="/settings">Manage my account</Link>
  </section>
}
