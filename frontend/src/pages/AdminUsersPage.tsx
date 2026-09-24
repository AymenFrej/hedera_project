import { FormEvent, useEffect, useState } from 'react'
import { createManagedUser, deleteManagedUser, getAuthUser, listManagedUsers, ManagedUser, updateManagedUserRole, restoreManagedUser, provisionManagedWallet, deleteMockUser, editManagedProfile } from '../api/client'
const roles = ['USER', 'ADMIN', 'AUDITOR', 'PLATFORM']
const mockWallet = (user: ManagedUser) => user.hederaAccountId === '0.0.mock' || user.hederaAccountId.startsWith('0.0.demo-')
export default function AdminUsersPage() {
  const [users, setUsers] = useState<ManagedUser[]>([])
  const [email, setEmail] = useState('')
  const [name, setName] = useState('')
  const [password, setPassword] = useState('')
  const [role, setRole] = useState('USER')
  const [message, setMessage] = useState('')
  const [error, setError] = useState('')
  const [busy, setBusy] = useState(false)
  const [editing, setEditing] = useState<ManagedUser | null>(null)
  const [restoreRoles, setRestoreRoles] = useState<Record<string,string>>({})
  const self = getAuthUser<{userId:string}>()?.userId
  async function load() { setUsers(await listManagedUsers()) }
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
      setMessage(mockWallet(created) ? 'User created in MOCK mode. No real wallet was created.' : 'User created with wallet ' + created.hederaAccountId)
    })
  }
  return <><h1>Manage users</h1>
    <p>Close disables login and retains the wallet. Restore enables login again. Only closed mock accounts without stored keys can be permanently deleted.</p>
    {message && <p role="status">{message}</p>}{error && <p role="alert">{error}</p>}
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
      <button className="button secondary" disabled={busy} onClick={() => void run(async () => {})}>Refresh users</button>
      {users.map(user => {
        const closed = user.role === 'DISABLED'
        return <div className="admin-user-row" key={user.id} style={{gridTemplateColumns:'minmax(180px,1fr) minmax(120px,160px) minmax(180px,2fr)'}}>
          <div><b>{user.displayName}</b><span>{user.email}</span><code>{user.hederaAccountId}</code>
            <span>{closed ? 'Closed - login disabled' : 'Active'} / {mockWallet(user) ? 'MOCK wallet' : 'Real wallet'}</span>
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
              {mockWallet(user) && <button className="danger-button" disabled={busy || user.id === self} onClick={() => {
                if(window.confirm('Permanently delete this closed mock user and mock wallet record? This cannot be undone.'))
                  void run(async () => { await deleteMockUser(user.id); setMessage('Mock account permanently deleted. Its email can be used again.') })
              }}>Delete mock account</button>}
            </> : <>
              {mockWallet(user) && <button className="button secondary" disabled={busy} onClick={() => {
                if(window.confirm('Create a real Hedera wallet for ' + user.email + '? The configured platform account pays the network fee.'))
                  void run(async () => { const updated = await provisionManagedWallet(user.id); setMessage('Real wallet assigned: '+updated.hederaAccountId) })
              }}>Create real wallet</button>}
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
