import { FormEvent, useEffect, useState } from 'react'
import { createManagedUser, deleteManagedUser, getAuthUser, listManagedUsers, ManagedUser, updateManagedUserRole } from '../api/client'
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
  const self = getAuthUser<{userId:string}>()?.userId
  async function load() { setUsers(await listManagedUsers()) }
  useEffect(() => { load().catch(e => setError(e.message)) }, [])
  async function run(work: () => Promise<void>) {
    setBusy(true); setError(''); setMessage('')
    try { await work(); await load() } catch(e) { setError((e as Error).message) } finally { setBusy(false) }
  }
  function submit(e: FormEvent) {
    e.preventDefault()
    void run(async () => { await createManagedUser({email:email.trim(), password, displayName:name.trim(), role}); setEmail(''); setName(''); setPassword(''); setMessage('User and wallet created.') })
  }
  return <><h1>Manage users</h1>{message && <p role="status">{message}</p>}{error && <p role="alert">{error}</p>}
    <section className="data-panel settings-card admin-create"><h2>Create user</h2><form onSubmit={submit}>
      <label>Display name<input required maxLength={128} value={name} onChange={e => setName(e.target.value)}/></label>
      <label>Email<input required type="email" maxLength={320} value={email} onChange={e => setEmail(e.target.value)}/></label>
      <label>Temporary password<input required type="password" minLength={8} maxLength={128} value={password} onChange={e => setPassword(e.target.value)}/></label>
      <label>Role<select value={role} onChange={e => setRole(e.target.value)}>{roles.map(r => <option key={r}>{r}</option>)}</select></label>
      <button className="button primary" disabled={busy}>{busy ? 'Working…' : 'Create user and wallet'}</button>
    </form></section>
    <section className="data-panel admin-users"><h2>Existing users</h2>
      <button className="button secondary" disabled={busy} onClick={() => void run(async () => {})}>Refresh users</button>
      {users.map(user => <div className="admin-user-row" key={user.id}>
        <div><b>{user.displayName}</b><span>{user.email}</span><code>{user.hederaAccountId}</code></div>
        <select aria-label={'Role for ' + user.email} disabled={busy || user.id === self || user.role === 'DISABLED'} value={user.role}
          onChange={e => { const next = e.target.value; void run(async () => { await updateManagedUserRole(user.id, next); setMessage('Role updated. Their existing sessions were revoked.') }) }}>
          {user.role === 'DISABLED' && <option>DISABLED</option>}{roles.map(r => <option key={r}>{r}</option>)}
        </select>
        <button className="danger-button" disabled={busy || user.id === self || user.role === 'DISABLED'}
          onClick={() => { if (window.confirm('Close ' + user.email + '? Their app access will stop; their Hedera wallet will be retained.')) void run(async () => { await deleteManagedUser(user.id); setMessage('Account closed.') }) }}>Delete</button>
      </div>)}
    </section>
  </>
}
