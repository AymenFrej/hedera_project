# Account access and regression checks

Permissions are enforced by the backend as well as sidebar filtering and route guards.

| Capability | USER | AUDITOR | ADMIN | PLATFORM |
| --- | --- | --- | --- | --- |
| Own wallet, profile, password, logout | Yes | Yes | Yes | Yes |
| Payments and tokens | Yes | No | Yes | No |
| Audit reading/verification | No | Yes | Yes | Yes |
| Submit audit event | No | No | Yes | Yes |
| Policies, approvals, manage users | No | No | Yes | Yes |
| Close own app account | Yes | Yes | No | No |

PLATFORM retains the existing operations-manager role; it is not a user's transaction signer.
Administrators cannot change their own role or close their own privileged account.
Another administrator can manage them. Public registration always creates a USER.
GET /accounts returns only the current user's wallet; managing users uses /admin/users.
Audit identity is resolved from the authenticated session, not request data.

Sessions expire after eight hours and are held in memory (backend restart signs everyone out).
Changing a password, changing a role, or closing an account revokes its sessions.
App closure disables login and marks the wallet CLOSED. It does not delete the on-chain
account or destroy retained encrypted keys. This is not a personal-data erasure mechanism.
The displayed balance is a stored balance, not a live Hedera query.

## Checks

Run backend: cd backend, then ./mvnw test (mvnw.cmd on Windows).
Run frontend: cd frontend, then npm test, npm run build, npm run lint.
Account integration tests use an isolated H2 database and a mocked wallet gateway.
They do not create live wallets or spend HBAR. Frontend tests cover the permission matrix,
empty successful responses, failed/successful deletion, logout, session expiry and profile updates.

Manually log in as each role at /login. Check sidebar links, open forbidden paths directly,
inspect My Wallet, update a profile and password, and verify logout returns to login.
For administrative creation and deletion, use disposable testnet users only; creating a
real wallet incurs network fees. After a role change, the affected user must sign in again.

## Remaining production work

This is still a development application: legacy SHA-256 password storage needs migration to
an adaptive password hash, sessions need a hardened production strategy, and wallet-key
backup/recovery and closure/fund recovery workflows need explicit design. Keep the key
encryption secret stable; changing it does not re-encrypt previously created wallets.
Other modules' placeholder actions are outside the account-management regression scope.
