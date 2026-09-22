import { Bell, Search, Sparkles } from 'lucide-react'
import { Outlet } from 'react-router-dom'
import Sidebar from './Sidebar'
export default function AppShell() { return <div className="app-shell"><Sidebar/><main className="main"><header className="topbar"><div className="crumb"><span>Workspace</span><b>/</b><span className="muted">Operations</span></div><div className="top-actions"><div className="search"><Search size={15}/><span>Search anything</span><kbd>⌘ K</kbd></div><Bell size={18} className="bell"/><div className="avatar">AM</div></div></header><div className="content"><Outlet/></div><footer className="footer"><span><span className="status-dot"/> All systems operational</span><span>Starter template · Hedera testnet ready</span></footer></main></div> }
