import { useEffect, useState } from 'react'
import { Routes, Route, Navigate, useLocation } from 'react-router-dom'
import AppShell from './layout/AppShell'
import FeaturePage from './pages/FeaturePage'
import TokensPage from './pages/TokensPage'
import PoliciesPage from './pages/PoliciesPage'
import ApprovalsPage from './pages/ApprovalsPage'
import AuditPage from './pages/AuditPage'
import AuthPage from './pages/AuthPage'
import RolePage from './pages/RolePage'
import SettingsPage from './pages/SettingsPage'
import AdminUsersPage from './pages/AdminUsersPage'
import AccountsPage from './pages/AccountsPage'
import AssistantPage from './pages/AssistantPage'
import PaymentsPage from './pages/PaymentsPage'
import { apiRequest, clearAuthToken, getAuthToken, getAuthUser, setAuthToken } from './api/client'
import { canAccess } from './access'
function Protected() {
  const {pathname} = useLocation()
  const [checked, setChecked] = useState('')
  const [error, setError] = useState('')
  useEffect(() => {
    let active = true
    setError('')
    if (!getAuthToken()) { setChecked(pathname); return }
    apiRequest<{token:string; role:string}>('/auth/me').then(user => {
      if (active) { setAuthToken(user.token, user); setChecked(pathname) }
    }).catch(e => { if (active) { setError(e.message); setChecked(pathname) } })
    return () => { active = false }
  }, [pathname])
  if (!getAuthToken()) return <Navigate to="/login" replace/>
  if (checked !== pathname) return <p>Checking session…</p>
  if (error) return <div role="alert">{error}<button onClick={() => { clearAuthToken(); window.location.assign('/login') }}>Sign in again</button></div>
  if (!canAccess(getAuthUser()?.role, pathname)) return <Navigate to="/workspace" replace/>
  return <AppShell/>
}
export default function App() {
  return <Routes>
    <Route path="/login" element={<AuthPage/>}/>
    <Route element={<Protected/>}>
      <Route path="/workspace" element={<RolePage/>}/>
      <Route path="/settings" element={<SettingsPage/>}/>
      <Route path="/admin/users" element={<AdminUsersPage/>}/>
      <Route path="/accounts" element={<AccountsPage/>}/>
      <Route path="/assistant" element={<AssistantPage/>}/>
      <Route path="/payments" element={<FeaturePage kind="payments"/>}/>
      <Route path="/tokens" element={<FeaturePage kind="tokens"/>}/>
      <Route path="/payments" element={<PaymentsPage/>}/>
      <Route path="/tokens" element={<TokensPage/>}/>
      <Route path="/audit" element={<AuditPage/>}/>
      <Route path="/policies" element={<PoliciesPage/>}/>
      <Route path="/approvals" element={<ApprovalsPage/>}/>
    </Route>
    <Route path="*" element={<Navigate to="/workspace" replace/>}/>
  </Routes>
}
