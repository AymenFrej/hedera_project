import { FormEvent, useEffect, useState } from 'react'
import { useNavigate } from 'react-router-dom'
import { changePassword, deleteAccount, getAuthUser, getCurrentAccount, logout, updateProfile } from '../api/client'
export default function SettingsPage() {
  const navigate = useNavigate()
  const user = getAuthUser()
  const [email, setEmail] = useState(user?.email ?? '')
  const [displayName, setDisplayName] = useState(user?.displayName ?? '')
  const [currentPassword, setCurrentPassword] = useState('')
  const [newPassword, setNewPassword] = useState('')
  const [confirmPassword, setConfirmPassword] = useState('')
  const [account, setAccount] = useState<Awaited<ReturnType<typeof getCurrentAccount>>>()
  const [message, setMessage] = useState('')
  const [error, setError] = useState('')
  const [busy, setBusy] = useState(false)
  useEffect(() => { getCurrentAccount().then(setAccount).catch(e => setError(e.message)) }, [])
  async function run(work: () => Promise<void>) {
    setBusy(true); setError(''); setMessage('')
    try { await work() } catch(e) { setError((e as Error).message) } finally { setBusy(false) }
  }
  function profile(e: FormEvent) {
    e.preventDefault()
    void run(async () => { await updateProfile(email.trim(), displayName.trim()); setMessage('Profile saved.') })
  }
  function password(e: FormEvent) {
    e.preventDefault()
    if (newPassword !== confirmPassword) { setError('New passwords do not match.'); return }
    void run(async () => { await changePassword(currentPassword, newPassword); await logout(); navigate('/login', {replace: true}) })
  }
  function remove() {
    if (!window.confirm('Close your app account? Login will be disabled. This does not delete the Hedera wallet or its on-chain history.')) return
    void run(async () => { await deleteAccount(); navigate('/login', {replace: true}) })
  }
  return <><h1>Account settings</h1>
    {message && <p role="status">{message}</p>}{error && <p role="alert">{error}</p>}
    <div className="settings-grid">
      <section className="data-panel settings-card"><h2>Profile</h2><p>Role: {user?.role}</p>
        <form onSubmit={profile}><label>Email<input type="email" required maxLength={320} value={email} onChange={e => setEmail(e.target.value)}/></label>
        <label>Display name<input required maxLength={128} value={displayName} onChange={e => setDisplayName(e.target.value)}/></label>
        <button className="button primary" disabled={busy}>Save profile</button></form>
      </section>
      <section className="data-panel settings-card"><h2>Password</h2><p>Changing your password signs out all sessions.</p>
        <form onSubmit={password}><label>Current password<input type="password" required autoComplete="current-password" value={currentPassword} onChange={e => setCurrentPassword(e.target.value)}/></label>
        <label>New password<input type="password" required minLength={8} maxLength={128} autoComplete="new-password" value={newPassword} onChange={e => setNewPassword(e.target.value)}/></label>
        <label>Confirm new password<input type="password" required minLength={8} maxLength={128} autoComplete="new-password" value={confirmPassword} onChange={e => setConfirmPassword(e.target.value)}/></label>
        <button className="button primary" disabled={busy}>Change password</button></form>
      </section>
      <section className="data-panel settings-card wallet-card"><h2>My wallet</h2>
        <p>Balances shown here are recorded values, not a live Hedera balance query.</p>
        <button className="button secondary" disabled={busy} onClick={() => void run(async () => { setAccount(await getCurrentAccount()) })}>Refresh wallet</button>
        <code>{account?.hederaAccountId ?? (error ? 'Wallet unavailable' : 'Loading…')}</code>
        <p>Status: {account?.status} · Recorded balance: {account?.balance} HBAR</p>
        <button className="button secondary" onClick={() => { void logout(); navigate('/login', {replace: true}) }}>Log out</button>
        {['USER', 'AUDITOR'].includes(user?.role ?? '') ?
          <button className="danger-button" disabled={busy} onClick={remove}>Close account</button> :
          <p>Another administrator must close a privileged account.</p>}
      </section>
    </div></>
}
