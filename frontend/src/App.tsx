import { Routes, Route, Navigate } from 'react-router-dom'
import AppShell from './layout/AppShell'
import Dashboard from './pages/Dashboard'
import FeaturePage from './pages/FeaturePage'
import AuditPage from './pages/AuditPage'
import PaymentsPage from './pages/PaymentsPage'
export default function App() { return <Routes><Route element={<AppShell/>}><Route path="/dashboard" element={<Dashboard/>}/><Route path="/accounts" element={<FeaturePage kind="accounts"/>}/><Route path="/payments" element={<PaymentsPage/>}/><Route path="/tokens" element={<FeaturePage kind="tokens"/>}/><Route path="/audit" element={<AuditPage/>}/><Route path="/policies" element={<FeaturePage kind="policies"/>}/><Route path="/approvals" element={<FeaturePage kind="approvals"/>}/><Route path="*" element={<Navigate to="/dashboard" replace/>}/></Route></Routes> }
