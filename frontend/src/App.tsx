import { Routes, Route, Navigate } from 'react-router-dom'
import AppShell from './layout/AppShell'
import Dashboard from './pages/Dashboard'
import FeaturePage from './pages/FeaturePage'
import AuditPage from './pages/AuditPage'
import AuthPage from './pages/AuthPage'
import { getAuthToken } from './api/client'
function Protected({ children }: { children: JSX.Element }) { return getAuthToken() ? children : <Navigate to="/login" replace/> }
export default function App() { return <Routes><Route path="/login" element={<AuthPage/>}/><Route element={<Protected><AppShell/></Protected>}><Route path="/dashboard" element={<Dashboard/>}/><Route path="/accounts" element={<FeaturePage kind="accounts"/>}/><Route path="/payments" element={<FeaturePage kind="payments"/>}/><Route path="/tokens" element={<FeaturePage kind="tokens"/>}/><Route path="/audit" element={<AuditPage/>}/><Route path="/policies" element={<FeaturePage kind="policies"/>}/><Route path="/approvals" element={<FeaturePage kind="approvals"/>}/><Route path="*" element={<Navigate to="/dashboard" replace/>}/></Route></Routes> }
