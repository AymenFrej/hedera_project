# Implemented feature UI

This UI uses the existing backend contracts. Payments, Tokens and contextual AI agents remain placeholders; this work does not implement them or enable real transfers.

| Feature | Screen and controls | Access |
| --- | --- | --- |
| Authentication | `/login`: email sign-in and registration | Public |
| Wallet | `/accounts`: assigned wallet ID, copy, refresh, stored balance clearly labelled | Every active role |
| Profile | `/settings`: edit email/name, change password with confirmation, logout, wallet refresh | Every active role |
| Account closure | Settings confirmation; on-chain wallet/history retained | USER, AUDITOR; privileged users require another administrator |
| User administration | `/admin/users`: create, search, filter, edit, change role, close/restore, provision real wallet; restricted mock deletion | ADMIN, PLATFORM |
| User administration | `/admin/users`: create real-wallet users, search, filter, edit, change role, close/restore | ADMIN, PLATFORM |
| Policy engine | `/policies`: backend rulebook, shared demo budgets, known counterparties, validated spend request, result/evidence links | ADMIN, PLATFORM |
| Approvals | `/approvals`: status filter, refresh, timestamps/actor, confirmed approve/reject, originating audit link | ADMIN, PLATFORM |
| Audit | `/audit`: search, anchor filter, actor/status/IDs, proof fields, Mirror Node verification | ADMIN, AUDITOR, PLATFORM |
| Synthetic audit event | Explicit confirmation; `DeveloperConsole / TEST_EVENT`, metadata `synthetic=true` | ADMIN, PLATFORM |

Navigation follows the existing role matrix. Backend authorization remains authoritative.

## Important distinctions

- Policy decisions and approvals use the real rule engine but its **shared demo budget ledger**, not a user's Hedera wallet. Approving does not execute a transfer.
- Reset requires confirmation because it resets budgets and clears all approval requests, across users. Existing audit events remain.
- An audit event is not necessarily anchored. Its anchor status is displayed separately from the business outcome.
- A failed verification or network error is not labelled tampering. Different available hashes are reported as a hash mismatch.
- Demo wallets are labelled as such. This UI does not silently replace or delete real wallets or stored keys.
- Every account shown by the account-management UI is expected to have a real Hedera wallet. Creation fails if provisioning cannot be confirmed.
For manual review, register a disposable user after configuring Hedera credentials. Check each allowed route, narrow/wide viewport layouts, keyboard focus, loading/failure states and logout.

## Tests

From `frontend`: `npm ci`, `npm test`, `npm run lint`, `npm run build`.

The interaction tests render the real React components with `react-test-renderer` (matching React 18), replacing only HTTP with fixtures. They cover policy validation/evidence links, cancelled reset, API errors, approvals, audit filtering and verification failure, synthetic event payloads, user search, wallet display, password confirmation and role navigation. No test fixture sends a real Hedera transaction.

From `backend`: `./mvnw test` (Windows: `.\mvnw.cmd test`).

For manual review, use the demo accounts documented in README. Check each allowed route, narrow/wide viewport layouts, keyboard focus, loading/failure states and logout. Confirm destructive actions only against disposable demo data. Browser visual review is separate from component tests.
