import { NavLink, useNavigate } from 'react-router-dom'
import { Activity, ArrowLeftRight, Bot, Coins, FileCheck2, LayoutDashboard, Settings, ShieldCheck, Users, LogOut } from 'lucide-react'
import { getAuthUser, logout } from '../api/client'
import { canAccess } from '../access'
const links = [
  { to: '/workspace', label: 'Workspace', icon: LayoutDashboard },
  { to: '/accounts', label: 'My Wallet', icon: Users },
  { to: '/assistant', label: 'AI Assistant', icon: Bot },
  { to: '/payments', label: 'Payments', icon: ArrowLeftRight },
  { to: '/tokens', label: 'Tokens', icon: Coins },
  { to: '/audit', label: 'Audit & Monitoring', icon: Activity },
  { to: '/policies', label: 'Policies', icon: ShieldCheck },
  { to: '/approvals', label: 'Approvals', icon: FileCheck2 },
  { to: '/admin/users', label: 'Manage Users', icon: Users },
  { to: '/settings', label: 'Settings', icon: Settings },
]
export default function Sidebar() {
  const role = getAuthUser()?.role
  const navigate = useNavigate()
  return <aside className="sidebar">
    <div className="brand"><Bot size={24}/><div><strong>Hedera</strong><span>Agent Platform</span></div></div>
    <div className="workspace">{role} workspace</div>
    <nav>{links.filter(link => canAccess(role, link.to)).map(({to, label, icon: Icon}) =>
      <NavLink key={to} to={to} className={({isActive}) => isActive ? 'nav-link active' : 'nav-link'}><Icon size={17}/>{label}{['/payments','/tokens'].includes(to) && <small className="mock-pill">MOCK</small>}</NavLink>
    )}</nav>
    <div className="sidebar-bottom"><button className="button secondary" onClick={() => { void logout(); navigate('/login', {replace: true}) }}><LogOut size={17}/>Log out</button></div>
  </aside>
}
