import { Link, Outlet, useLocation } from 'react-router-dom'
import { getAuthUser } from '../api/client'
import Sidebar from './Sidebar'

const titles: Record<string, string> = { '/workspace': 'Overview', '/accounts': 'My Wallet', '/admin/users': 'Manage Users', '/audit': 'Audit & Monitoring', '/policies': 'Policies', '/approvals': 'Approvals', '/settings': 'Settings', '/payments': 'Payments', '/tokens': 'Tokens · Mock' }

export default function AppShell() {
  const { pathname } = useLocation()
  const user = getAuthUser()
  return <div className="app-shell"><Sidebar/><main className="main">
    <header className="topbar"><div className="crumb"><span>Workspace</span><b>/</b><span>{titles[pathname] ?? 'Operations'}</span></div>
      <Link to="/settings" className="top-actions user-menu" aria-label="Manage my profile"><span>{user?.displayName}<small>{user?.role}</small></span><div className="avatar">{user?.displayName?.slice(0, 2).toUpperCase() || 'ME'}</div></Link>
    </header><div className="content"><Outlet/></div>
    <footer className="footer"><span>Development workspace</span><span>Tokens is a placeholder</span></footer>
  </main></div>
}
