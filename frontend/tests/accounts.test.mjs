import {test, beforeEach} from 'node:test'
import assert from 'node:assert/strict'
import {readFile} from 'node:fs/promises'
import ts from 'typescript'

async function sourceModule(path) {
  const source = (await readFile(new URL(path, import.meta.url), 'utf8'))
    .replace('import.meta.env.VITE_API_BASE_URL', 'undefined')
  const {outputText} = ts.transpileModule(source, {compilerOptions: {module: ts.ModuleKind.ESNext, target: ts.ScriptTarget.ES2022}})
  return import('data:text/javascript;base64,' + Buffer.from(outputText).toString('base64'))
}
const client = await sourceModule('../src/api/client.ts')
const {canAccess} = await sourceModule('../src/access.ts')
let redirects
beforeEach(() => {
  const storage = new Map()
  globalThis.localStorage = {getItem: key => storage.get(key) ?? null, setItem: (key,value) => storage.set(key,value), removeItem: key => storage.delete(key)}
  redirects = []
  globalThis.window = {location: {assign: path => redirects.push(path)}}
  client.setAuthToken('test-token', {role:'ADMIN'})
})
test('role navigation matrix is default-deny', () => {
  for (const role of ['USER','AUDITOR','ADMIN','PLATFORM']) {
    for (const path of ['/workspace','/accounts','/settings']) assert.equal(canAccess(role,path),true)
    assert.equal(canAccess(role,'/admin/users'), ['ADMIN','PLATFORM'].includes(role))
    assert.equal(canAccess(role,'/audit'), role !== 'USER')
    assert.equal(canAccess(role,'/payments'), ['ADMIN','USER'].includes(role))
    assert.equal(canAccess(role,'/tokens'), ['ADMIN','USER'].includes(role))
    assert.equal(canAccess(role,'/policies'), ['ADMIN','PLATFORM'].includes(role))
    assert.equal(canAccess(role,'/approvals'), ['ADMIN','PLATFORM'].includes(role))
    assert.equal(canAccess(role,'/unknown'),false)
  }
  assert.equal(canAccess('DISABLED','/accounts'),false)
  assert.equal(canAccess(undefined,'/accounts'),false)
})
test('empty successful responses do not fail JSON parsing', async () => {
  globalThis.fetch = async () => new Response(null, {status:204})
  await client.changePassword('old','new')
  await client.deleteManagedUser('target')
})
test('failed account deletion preserves session', async () => {
  globalThis.fetch = async () => new Response('{"error":"Cannot close account"}', {status:403})
  await assert.rejects(client.deleteAccount(), /Cannot close account/)
  assert.equal(client.getAuthToken(),'test-token')
})
test('successful account deletion clears session', async () => {
  globalThis.fetch = async () => new Response(null, {status:200})
  await client.deleteAccount()
  assert.equal(client.getAuthToken(),null)
})
test('logout clears session immediately even when server is offline', async () => {
  globalThis.fetch = async (_url, options) => {
    assert.equal(options.headers.Authorization,'Bearer test-token')
    assert.equal(client.getAuthToken(),null)
    throw new Error('offline')
  }
  await client.logout()
  assert.equal(client.getAuthUser(),null)
})
test('expired session redirects to login', async () => {
  globalThis.fetch = async () => new Response('{"error":"Invalid session"}', {status:401})
  await assert.rejects(client.getCurrentAccount(), /Invalid session/)
  assert.equal(client.getAuthToken(),null)
  assert.deepEqual(redirects,['/login'])
})
test('profile response refreshes the stored user', async () => {
  globalThis.fetch = async () => Response.json({token:'test-token',role:'ADMIN',email:'new@example.test',displayName:'New'})
  await client.updateProfile('new@example.test','New')
  assert.equal(client.getAuthUser().displayName,'New')
})
test('policy refusal keeps main error message and authenticated session', async () => {
  globalThis.fetch = async (_url, options) => {
    assert.equal(options.headers.Authorization,'Bearer test-token')
    return new Response('{"error":"Approval was already approved"}', {status:409})
  }
  await assert.rejects(client.answerApproval('approval_test','approve'), /Approval was already approved/)
  assert.equal(client.getAuthToken(),'test-token')
  assert.deepEqual(redirects,[])
})
test('admin lifecycle actions use separate authenticated endpoints', async () => {
  const calls = []
  globalThis.fetch = async (url, options) => {
    calls.push([url.split('/api/v1')[1], options.method])
    assert.equal(options.headers.Authorization,'Bearer test-token')
    return options.method === 'DELETE' ? new Response(null,{status:204}) : Response.json({id:'u',role:'USER'})
  }
  await client.editManagedProfile('u','user@example.test','User')
  await client.deleteManagedUser('u')
  await client.restoreManagedUser('u','USER')
  assert.deepEqual(calls,[
    ['/admin/users/u/profile','PUT'],['/admin/users/u','DELETE'],
    ['/admin/users/u/restore','POST'],
  ])
})
