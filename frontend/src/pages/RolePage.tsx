import { Link } from 'react-router-dom'
import { getAuthUser } from '../api/client'
import { canAccess } from '../access'
const actions = [
  ['/accounts', 'View my wallet'], ['/settings', 'Account settings'],
  ['/admin/users', 'Manage users'], ['/audit', 'Review audit history'],
  ['/payments', 'Payments'], ['/tokens', 'Tokens'],
  ['/policies', 'Policies'], ['/approvals', 'Approvals'],
]
export default function RolePage() {
  const user = getAuthUser()
  return <section className="data-panel role-panel"><p className="eyebrow">{user?.role} WORKSPACE</p>
    <h1>Welcome, {user?.displayName}</h1><p>{user?.email}</p>
    <div className="role-actions">{actions.filter(([path]) => canAccess(user?.role, path)).map(([path, label]) =>
      <Link key={path} className="button secondary" to={path}>{label}</Link>)}</div>
  </section>
}
