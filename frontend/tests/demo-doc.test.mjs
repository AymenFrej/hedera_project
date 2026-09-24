import { test } from 'node:test'
import assert from 'node:assert/strict'
import { readFile } from 'node:fs/promises'

// The demo walkthrough is read aloud at a projector: every control it tells the presenter to
// click has to exist on the screen. A UI rename is silent until someone is standing in front of
// judges looking for a button that is not there, so the doc is checked against the real TSX.

const doc = await readFile(new URL('../../demo/policies.md', import.meta.url), 'utf8')
const screens = await Promise.all(
  ['PoliciesPage', 'ApprovalsPage'].map(name =>
    readFile(new URL(`../src/pages/${name}.tsx`, import.meta.url), 'utf8'),
  ),
)
const ui = screens.join('\n')

/** Every "click **Label**" instruction in the walkthrough. */
const clickTargets = [...doc.matchAll(/click \*\*(.+?)\*\*/gi)].map(match => match[1])

test('the walkthrough only tells the presenter to click buttons that exist', () => {
  assert.ok(clickTargets.length > 0, 'walkthrough names no controls to click')
  const missing = clickTargets.filter(label => !ui.includes(`>${label}<`))
  assert.deepEqual(missing, [], `walkthrough names controls the UI does not render: ${missing}`)
})
