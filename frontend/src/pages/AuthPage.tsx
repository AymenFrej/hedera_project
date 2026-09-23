import { FormEvent, useState } from 'react'
import { useNavigate } from 'react-router-dom'
import { apiRequest, setAuthToken } from '../api/client'

type AuthResponse = { token: string; userId: string; email: string; displayName: string; role: string; account: { hederaAccountId: string } }

export default function AuthPage() {
  const navigate = useNavigate()
  const [register, setRegister] = useState(false)
  const [email, setEmail] = useState('')
  const [password, setPassword] = useState('')
  const [displayName, setDisplayName] = useState('')
  const [error, setError] = useState('')
  const [busy, setBusy] = useState(false)
  async function submit(event: FormEvent) {
    event.preventDefault(); setBusy(true); setError('')
    try {
      const response = await apiRequest<AuthResponse>(register ? '/auth/register' : '/auth/login', { method: 'POST', body: JSON.stringify({ email, password, displayName }) })
      setAuthToken(response.token); navigate('/dashboard', { replace: true })
    } catch (e) { setError(e instanceof Error ? e.message : 'Unable to sign in') } finally { setBusy(false) }
  }
  return <main className="auth-page"><section className="auth-card"><div className="brand auth-brand"><div className="brand-mark">✦</div><div><strong>Hedera Ops</strong><span>AGENT PLATFORM</span></div></div><p className="eyebrow">SECURE WORKSPACE</p><h1>{register ? 'Create your workspace' : 'Welcome back'}</h1><p className="subtitle">{register ? 'Register with email and receive a Hedera wallet automatically.' : 'Sign in to access your agents and wallet.'}</p><form onSubmit={submit}>{register && <label>Display name<input value={displayName} onChange={e => setDisplayName(e.target.value)} placeholder="Your name" /></label>}<label>Email<input type="email" required value={email} onChange={e => setEmail(e.target.value)} placeholder="you@company.com" /></label><label>Password<input type="password" required minLength={8} value={password} onChange={e => setPassword(e.target.value)} placeholder="At least 8 characters" /></label>{error && <div className="auth-error">{error}</div>}<button className="button primary auth-submit" disabled={busy}>{busy ? 'Working…' : register ? 'Register & assign wallet' : 'Sign in'}</button></form><button className="auth-toggle" onClick={() => { setRegister(!register); setError('') }}>{register ? 'Already have an account? Sign in' : 'New here? Register with email'}</button></section></main>
}
