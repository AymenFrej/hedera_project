import { ShieldCheck, UserRound, FileSearch, Server } from 'lucide-react'
import { getAuthUser } from '../api/client'

const details = {
  USER: { title: 'User Workspace', description: 'Manage your wallet, accounts, payments, and assets.', icon: UserRound, actions: ['View wallet', 'Ask AccountAgent', 'Start a payment'] },
  ADMIN: { title: 'Admin Console', description: 'Manage platform users, policies, approvals, and operational settings.', icon: ShieldCheck, actions: ['Manage users', 'Review policies', 'Configure approvals'] },
  AUDITOR: { title: 'Auditor Workspace', description: 'Review activity history and verify the platform audit trail.', icon: FileSearch, actions: ['Browse audit events', 'Verify HCS proofs', 'Export activity'] },
  PLATFORM: { title: 'Platform Operations', description: 'Internal service workspace for ledger and agent operations.', icon: Server, actions: ['Inspect services', 'Review agent health', 'Manage integrations'] },
} as const

export default function RolePage() {
  const user = getAuthUser(); const role = (user?.role ?? 'USER') as keyof typeof details; const item = details[role] ?? details.USER; const Icon = item.icon
  return <><div className="page-heading"><div><p className="eyebrow">ROLE / {role}</p><h1>{item.title}</h1><p className="subtitle">{item.description}</p></div><div className="row-icon"><Icon size={20}/></div></div><div className="data-panel role-panel"><div className="role-welcome"><Icon size={24}/><div><b>Welcome, {user?.displayName ?? 'operator'}</b><span>{user?.email}</span></div></div><div className="role-actions">{item.actions.map(action => <button className="button secondary" key={action}>{action}</button>)}</div></div></>
}
