import { FormEvent, useEffect, useState } from 'react'
import { createManagedUser, deleteManagedUser, getAuthUser, listManagedUsers, ManagedUser, updateManagedUserRole, restoreManagedUser, editManagedProfile, runAccountAgent } from '../api/client'
const roles = ['USER', 'ADMIN', 'AUDITOR', 'PLATFORM']
export default function AdminUsersPage() {
  const [users, setUsers] = useState<ManagedUser[]>([])
  const [email, setEmail] = useState('')
  const [name, setName] = useState('')
  const [password, setPassword] = useState('')
  const [role, setRole] = useState('USER')
  const [message, setMessage] = useState('')
  const [error, setError] = useState('')
  const [busy, setBusy] = useState(false)
  const [loading, setLoading] = useState(true)
  const [search, setSearch] = useState('')
  const [status, setStatus] = useState('ALL')
  const [editing, setEditing] = useState<ManagedUser | null>(null)
  const [restoreRoles, setRestoreRoles] = useState<Record<string,string>>({})
  const [agentPrompt, setAgentPrompt] = useState('')
  const [agentResult, setAgentResult] = useState('')
  const self = getAuthUser<{userId:string}>()?.userId
  async function load() { setLoading(true); try { setUsers(await listManagedUsers()) } finally { setLoading(false) } }
  useEffect(() => { load().catch(e => setError(e.message)) }, [])
  async function run(work: () => Promise<void>) {
    setBusy(true); setError(''); setMessage('')
    try { await work(); await load() } catch(e) { setError((e as Error).message) } finally { setBusy(false) }
  }
  function submit(e: FormEvent) {
    e.preventDefault()
    void run(async () => {
      const created = await createManagedUser({email:email.trim(), password, displayName:name.trim(), role})
      setEmail(''); setName(''); setPassword('')
      setMessage('User created with real wallet ' + created.hederaAccountId)
    })
  }
  function runAgent(e: FormEvent) {
    e.preventDefault()
    void run(async () => {
      setAgentResult('')
      const result = await runAccountAgent(agentPrompt.trim())
      if (result.status !== 'COMPLETED') throw new Error(result.message)
      setAgentResult(result.message)
      setAgentPrompt('')
    })
  }
  return <><h1>Manage users</h1>
    <p>Close disables login and retains the real Hedera wallet. Restore enables login again.</p>
    <p>New accounts require a real Hedera wallet. Creation fails without saving an account if Hedera is unavailable.</p>
    {busy && <p role="status">Saving changes. Wallet creation may take a few minutes.</p>}
    {message && <p role="status">{message}</p>}{error && <p role="alert">{error}</p>}
    <section className="data-panel settings-card admin-create"><h2>Account agent</h2>
      <p>Tell the account agent what to do. It resolves people from the managed accounts and uses only approved account tools.</p>
      <form onSubmit={runAgent}>
        <label>Request<textarea value={agentPrompt} onChange={e => setAgentPrompt(e.target.value)} placeholder="e.g. Make Aymen's name Ahmed" required maxLength={500} rows={3}/></label>
        <button className="button secondary" disabled={busy || !agentPrompt.trim()}>{busy ? 'Thinking…' : 'Ask account agent'}</button>
      </form>
      {agentResult && <p role="status">{agentResult}</p>}
    </section>
    <section className="data-panel settings-card admin-create"><h2>Create user</h2><form onSubmit={submit}>
      <label>Display name<input required maxLength={128} value={name} onChange={e => setName(e.target.value)}/></label>
      <label>Email<input required type="email" maxLength={320} value={email} onChange={e => setEmail(e.target.value)}/></label>
      <label>Temporary password<input required type="password" minLength={8} maxLength={128} value={password} onChange={e => setPassword(e.target.value)}/></label>
      <label>Role<select value={role} onChange={e => setRole(e.target.value)}>{roles.map(r => <option key={r}>{r}</option>)}</select></label>
      <button className="button primary" disabled={busy}>{busy ? 'Working...' : 'Create user and wallet'}</button>
    </form></section>
    {editing && <section className="data-panel settings-card"><h2>Edit user</h2>
      <form onSubmit={e => { e.preventDefault(); const current = editing; void run(async () => { await editManagedProfile(current.id,current.email,current.displayName); setEditing(null); setMessage('User details saved.') }) }}>
        <label>Email<input type="email" required maxLength={320} value={editing.email} onChange={e => setEditing({...editing,email:e.target.value})}/></label>
        <label>Display name<input required maxLength={128} value={editing.displayName} onChange={e => setEditing({...editing,displayName:e.target.value})}/></label>
        <button className="button primary" disabled={busy}>Save user</button>
        <button type="button" className="button secondary" disabled={busy} onClick={() => setEditing(null)}>Cancel</button>
      </form>
    </section>}
    <section className="data-panel admin-users"><h2>Existing users</h2>
      <div className="feature-toolbar"><label>Search users<input type="search" placeholder="Name, email or wallet ID" value={search} onChange={e => setSearch(e.target.value)}/></label>
        <label>Account status<select value={status} onChange={e => setStatus(e.target.value)}><option>ALL</option><option>ACTIVE</option><option>CLOSED</option></select></label>
        <button className="button secondary" disabled={busy || loading} onClick={() => void run(async () => {})}>Refresh users</button></div>
      {loading && <p role="status">Loading users…</p>}
      {!loading && !users.some(user => (status === 'ALL' || (user.role === 'DISABLED' ? 'CLOSED' : 'ACTIVE') === status) && [user.email, user.displayName, user.hederaAccountId].some(value => value.toLowerCase().includes(search.toLowerCase()))) && <p>{error ? 'Users unavailable. Refresh to retry.' : 'No matching users.'}</p>}
      {users.filter(user => (status === 'ALL' || (user.role === 'DISABLED' ? 'CLOSED' : 'ACTIVE') === status) && [user.email, user.displayName, user.hederaAccountId].some(value => value.toLowerCase().includes(search.toLowerCase()))).map(user => {
        const closed = user.role === 'DISABLED'
        return <div className="admin-user-row" key={user.id} style={{gridTemplateColumns:'minmax(180px,1fr) minmax(120px,160px) minmax(180px,2fr)'}}>
          <div><b>{user.displayName}</b><span>{user.email}</span><code>{user.hederaAccountId}</code>
            <span>{closed ? 'Closed - login disabled' : 'Active'} / Real wallet</span>
          </div>
          <select aria-label={(closed ? 'Restore role for ' : 'Role for ') + user.email}
            disabled={busy || user.id === self} value={closed ? restoreRoles[user.id] ?? 'USER' : user.role}
            onChange={e => { const next = e.target.value; if(closed) setRestoreRoles({...restoreRoles,[user.id]:next}); else void run(async () => { await updateManagedUserRole(user.id,next); setMessage('Role updated; old sessions revoked.') }) }}>
            {roles.map(r => <option key={r}>{r}</option>)}
          </select>
          <div style={{display:'flex',gap:8,flexWrap:'wrap'}}>
            <button className="button secondary" disabled={busy} onClick={() => setEditing({...user})}>Edit</button>
            {closed ? <>
              <button className="button primary" disabled={busy || user.id === self} onClick={() => {
                if(window.confirm('Restore login for ' + user.email + ' as ' + (restoreRoles[user.id] ?? 'USER') + '?'))
                  void run(async () => { await restoreManagedUser(user.id,restoreRoles[user.id] ?? 'USER'); setMessage('Account restored. The existing password still works.') })
              }}>Restore</button>
            </> : <>
              <button className="danger-button" disabled={busy || user.id === self} onClick={() => {
                if(window.confirm('Close ' + user.email + '? Login will stop. The wallet and keys are retained; an admin can restore access.'))
                  void run(async () => { await deleteManagedUser(user.id); setMessage('Account closed. Use Restore to enable login again.') })
              }}>Close account</button>
            </>}
          </div>
        </div>
      })}
    </section>
  </>
}
