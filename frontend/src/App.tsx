import { Routes, Route, Navigate } from 'react-router-dom'
import AppShell from './layout/AppShell'
import Dashboard from './pages/Dashboard'
import FeaturePage from './pages/FeaturePage'
import AuditPage from './pages/AuditPage'
import AuthPage from './pages/AuthPage'
import { getAuthToken } from './api/client'
import RolePage from './pages/RolePage'
import SettingsPage from './pages/SettingsPage'
function Protected({ children }: { children: React.ReactElement }) { return getAuthToken() ? children : <Navigate to="/login" replace/> }
export default function App() { return <Routes><Route path="/login" element={<AuthPage/>}/><Route element={<Protected><AppShell/></Protected>}><Route path="/workspace" element={<RolePage/>}/><Route path="/settings" element={<SettingsPage/>}/><Route path="/dashboard" element={<Dashboard/>}/><Route path="/accounts" element={<FeaturePage kind="accounts"/>}/><Route path="/payments" element={<FeaturePage kind="payments"/>}/><Route path="/tokens" element={<FeaturePage kind="tokens"/>}/><Route path="/audit" element={<AuditPage/>}/><Route path="/policies" element={<FeaturePage kind="policies"/>}/><Route path="/approvals" element={<FeaturePage kind="approvals"/>}/><Route path="*" element={<Navigate to="/workspace" replace/>}/></Route></Routes> }
