import { test, beforeEach, afterEach } from 'node:test'
import assert from 'node:assert/strict'
import { readFile } from 'node:fs/promises'
import React from 'react'
import { create, act } from 'react-test-renderer'
import { MemoryRouter } from 'react-router-dom'
import ts from 'typescript'

// Compile the actual TSX screens in memory. Test fixtures replace HTTP only;
// component state, form handlers, links and role controls are the real code.
const modules = new Map()
async function moduleUrl(url) {
  if (modules.has(url.href)) return modules.get(url.href)
  const source = (await readFile(url, 'utf8')).replaceAll('import.meta.env.VITE_API_BASE_URL', 'undefined')
  let { outputText } = ts.transpileModule(source, { compilerOptions: {
    module: ts.ModuleKind.ESNext, target: ts.ScriptTarget.ES2022, jsx: ts.JsxEmit.ReactJSX,
  } })
  for (const match of [...outputText.matchAll(/from ["']([^"']+)["']/g)]) {
    const name = match[1]
    let resolved
    if (name.startsWith('.')) {
      const target = new URL(name, url)
      try { resolved = await moduleUrl(new URL(target.href + '.ts')) }
      catch (error) {
        if (error.code !== 'ENOENT') throw error
        resolved = await moduleUrl(new URL(target.href + '.tsx'))
      }
    } else resolved = import.meta.resolve(name)
    outputText = outputText.replaceAll(match[0], `from ${JSON.stringify(resolved)}`)
  }
  const result = 'data:text/javascript;base64,' + Buffer.from(outputText).toString('base64')
  modules.set(url.href, result)
  return result
}
const screen = async name => (await import(await moduleUrl(new URL(`../src/pages/${name}.tsx`, import.meta.url)))).default
const Policies = await screen('PoliciesPage')
const Approvals = await screen('ApprovalsPage')
const Audit = await screen('AuditPage')
const Admin = await screen('AdminUsersPage')
const Settings = await screen('SettingsPage')
const Accounts = await screen('AccountsPage')
const Auth = await screen('AuthPage')
const Sidebar = (await import(await moduleUrl(new URL('../src/layout/Sidebar.tsx', import.meta.url)))).default
let tree, calls, fixtures
// Envelopes arrive in tinybars, as the API sends them: 500 ℏ, 300 ℏ, 200 ℏ.
const state = { envelopes: { RENT: 50_000_000_000, ESSENTIALS: 30_000_000_000, EMERGENCY: 20_000_000_000 }, knownCounterparties: ['landlord-tunis'] }
const event = { id: 'audit_1', agent: 'PolicyAgent', action: 'DECIDE', status: 'SUCCESS', createdAt: null, anchorStatus: 'ANCHORED', actorId: 'admin_demo', actorType: 'USER', sequenceNumber: 1, payloadHash: 'abc' }
beforeEach(() => {
  const storage = new Map([['hedera.auth.token', 'test-token'], ['hedera.auth.user', JSON.stringify({ role: 'ADMIN', userId: 'admin_demo' })]])
  globalThis.localStorage = { getItem: key => storage.get(key) ?? null, setItem: (key, value) => storage.set(key, value), removeItem: key => storage.delete(key) }
  globalThis.window = { confirm: () => false, location: { assign() {} } }
  fixtures = { '/policies/state': state, '/policies': [{ ruleId: 'policy.ok', verdict: 'ALLOW', reason: 'Allowed by policy' }], '/audit/status': { ledgerActive: true }, '/audit': [event], '/audit/audit_1': event }
  calls = []
  globalThis.fetch = async (url, options) => {
    const path = new URL(url).pathname.replace('/api/v1', '')
    calls.push({ path, ...options })
    const result = fixtures[path]
    if (result instanceof Error) throw result
    if (result === undefined) throw new Error(`Unexpected request: ${path}`)
    return Response.json(result)
  }
})
afterEach(() => { if (tree) act(() => tree.unmount()); tree = undefined })
async function mount(Component, path = '/') { await act(async () => { tree = create(React.createElement(MemoryRouter, { initialEntries: [path] }, React.createElement(Component))) }) }
const text = node => typeof node === 'string' ? node : (Array.isArray(node) ? node : node?.children ?? []).map(text).join(' ')
const button = label => tree.root.findAllByType('button').find(node => text(node).includes(label))
const flush = work => act(async () => { work(); await new Promise(resolve => setImmediate(resolve)) })

test('policy screen loads real rulebook, validates amounts and links the returned evidence', async () => {
  fixtures['/policies/decide'] = { verdict: 'HOLD', ruleId: 'emergency.human', reason: 'Needs review', balanceAfter: null, approvalId: 'approval_1', auditEventId: 'audit_1', anchored: true, envelopes: state.envelopes }
  await mount(Policies)
  assert.match(text(tree.toJSON()), /policy.ok/)
  const amount = tree.root.findByProps({ type: 'number' })
  // Amounts are entered in ℏ, so 1.5 is real money and must be accepted; 0 never is.
  await flush(() => amount.props.onChange({ target: { value: '0' } }))
  await flush(() => tree.root.findByType('form').props.onSubmit({ preventDefault() {} }))
  assert.match(text(tree.toJSON()), /positive amount/)
  assert.equal(calls.filter(call => call.path === '/policies/decide').length, 0)
  await flush(() => amount.props.onChange({ target: { value: '1.5' } }))
  await flush(() => tree.root.findByType('form').props.onSubmit({ preventDefault() {} }))
  // 1.5 ℏ reaches the engine as 150000000 tinybars.
  assert.equal(JSON.parse(calls.find(call => call.path === '/policies/decide').body).amount, 150_000_000)
  await flush(() => amount.props.onChange({ target: { value: '50' } }))
  await flush(() => tree.root.findByType('form').props.onSubmit({ preventDefault() {} }))
  const links = tree.root.findAllByType('a').map(node => node.props.href)
  assert.ok(links.includes('/approvals?request=approval_1'))
  assert.ok(links.includes('/audit?event=audit_1'))
})

test('reset cancellation never calls the destructive endpoint', async () => {
  await mount(Policies)
  await flush(() => button('Reset shared demo').props.onClick())
  assert.equal(calls.some(call => call.path.includes('/reset')), false)
})

test('policy load failures show an error and keep evaluation disabled', async () => {
  fixtures['/policies/state'] = new Error('Connection unavailable')
  await mount(Policies)
  assert.match(text(tree.toJSON()), /Connection unavailable/)
  assert.equal(button('Evaluate request').props.disabled, true)
})

test('approval filters, confirmation and decision history use the backend response', async () => {
  const pending = { id: 'approval_1', status: 'PENDING', amount: 5_000_000_000, counterparty: 'landlord', envelope: 'EMERGENCY', ruleId: 'emergency.human', reason: 'Needs review', taskId: 'audit_1' }
  fixtures['/policies/approvals'] = [pending, { ...pending, id: 'approval_2', status: 'REJECTED' }]
  fixtures['/policies/approvals/approval_1/approve'] = { ...pending, status: 'APPROVED', decidedAt: '2026-09-24T10:00:00Z', decidedBy: 'admin_demo' }
  await mount(Approvals)
  await flush(() => tree.root.findByType('select').props.onChange({ target: { value: 'PENDING' } }))
  assert.equal(tree.root.findAllByType('article').length, 1)
  await flush(() => button('Approve').props.onClick())
  assert.equal(calls.some(call => call.method === 'POST'), false)
  window.confirm = () => true
  await flush(() => button('Approve').props.onClick())
  assert.equal(tree.root.findAllByType('article').length, 0)
  await flush(() => tree.root.findByType('select').props.onChange({ target: { value: 'APPROVED' } }))
  assert.match(text(tree.toJSON()), /admin_demo/)
  assert.equal(button('Approve'), undefined)
})

test('audit deep link loads one event and network failure is not called tampering', async () => {
  fixtures['/audit/audit_1/verification'] = new Error('Mirror Node unavailable')
  await mount(Audit, '/audit?event=audit_1')
  assert.ok(calls.some(call => call.path === '/audit/audit_1'))
  assert.equal(calls.some(call => call.path === '/audit'), false)
  await flush(() => button('Verify').props.onClick())
  assert.match(text(tree.toJSON()), /Verification not confirmed/)
  assert.doesNotMatch(text(tree.toJSON()), /Tampering detected/)
})

test('audit search filters events and synthetic recording is explicitly identified', async () => {
  await mount(Audit)
  await flush(() => tree.root.findByProps({ type: 'search' }).props.onChange({ target: { value: 'no-match' } }))
  assert.match(text(tree.toJSON()), /No matching audit events/)
  window.confirm = () => true
  await flush(() => button('Record test event').props.onClick())
  const payload = JSON.parse(calls.find(call => call.method === 'POST').body)
  assert.deepEqual(payload, { agent: 'DeveloperConsole', action: 'TEST_EVENT', status: 'SUCCESS', metadata: { synthetic: 'true' } })
})

test('admin users can be searched and filtered without altering accounts', async () => {
  fixtures['/admin/users'] = [{ id: 'one', email: 'one@example.test', displayName: 'One', role: 'USER', hederaAccountId: '0.0.123' }, { id: 'two', email: 'two@example.test', displayName: 'Two', role: 'DISABLED', hederaAccountId: '0.0.124' }]
  await mount(Admin)
  await flush(() => tree.root.findByProps({ type: 'search' }).props.onChange({ target: { value: 'one@example' } }))
  assert.match(text(tree.toJSON()), /one@example/)
  assert.doesNotMatch(text(tree.toJSON()), /two@example/)
  assert.ok(calls.every(call => !call.method))
})

test('sidebar only exposes allowed routes for every role', async () => {
  for (const role of ['USER', 'AUDITOR', 'ADMIN', 'PLATFORM']) {
    localStorage.setItem('hedera.auth.user', JSON.stringify({ role }))
    await mount(Sidebar)
    const links = tree.root.findAllByType('a').map(node => node.props.href)
    assert.equal(links.includes('/admin/users'), ['ADMIN', 'PLATFORM'].includes(role))
    assert.equal(links.includes('/policies'), ['ADMIN', 'PLATFORM'].includes(role))
    assert.equal(links.includes('/audit'), role !== 'USER')
    await act(async () => tree.unmount())
  }
})

test('wallet screen shows the actual returned ID and warns balance is not live', async () => {
  fixtures['/accounts/me'] = { id: 'account_1', hederaAccountId: '0.0.123456', status: 'ACTIVE', balance: '10' }
  await mount(Accounts)
  assert.match(text(tree.toJSON()), /0.0.123456/)
  assert.match(text(tree.toJSON()), /not a live balance query/)
  assert.ok(tree.root.findAllByType('a').some(link => link.props.href === '/settings'))
})

test('settings reject mismatched passwords without sending a request', async () => {
  fixtures['/accounts/me'] = { hederaAccountId: '0.0.123', balance: '0', status: 'ACTIVE' }
  await mount(Settings)
  const passwords = tree.root.findAllByProps({ type: 'password' })
  await flush(() => passwords[1].props.onChange({ target: { value: 'NewPassword123!' } }))
  await flush(() => passwords[2].props.onChange({ target: { value: 'Different123!' } }))
  await flush(() => tree.root.findAllByType('form')[1].props.onSubmit({ preventDefault() {} }))
  assert.match(text(tree.toJSON()), /New passwords do not match/)
  assert.equal(calls.some(call => call.path.endsWith('/password')), false)
  assert.equal(button('Close account'), undefined)
})

test('login reports an API error and allows retry without granting a session', async () => {
  localStorage.removeItem('hedera.auth.token')
  fixtures['/auth/login'] = new Error('Invalid email or password')
  await mount(Auth)
  await flush(() => tree.root.findByType('form').props.onSubmit({ preventDefault() {} }))
  assert.match(text(tree.toJSON()), /Invalid email or password/)
  assert.equal(button('Sign in').props.disabled, false)
  assert.equal(localStorage.getItem('hedera.auth.token'), null)
})
