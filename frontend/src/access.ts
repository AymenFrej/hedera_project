export type Role = 'USER' | 'ADMIN' | 'AUDITOR' | 'PLATFORM'
export const access: Record<string, readonly string[]> = {
  '/workspace': ['USER', 'ADMIN', 'AUDITOR', 'PLATFORM'],
  '/accounts': ['USER', 'ADMIN', 'AUDITOR', 'PLATFORM'],
  '/settings': ['USER', 'ADMIN', 'AUDITOR', 'PLATFORM'],
  '/payments': ['USER', 'ADMIN'], '/tokens': ['USER', 'ADMIN'],
  '/audit': ['ADMIN', 'AUDITOR', 'PLATFORM'],
  '/policies': ['ADMIN', 'PLATFORM'], '/approvals': ['ADMIN', 'PLATFORM'],
  '/admin/users': ['ADMIN', 'PLATFORM'],
}
export function canAccess(role: string | undefined, path: string) {
  return Boolean(role && access[path]?.includes(role))
}
