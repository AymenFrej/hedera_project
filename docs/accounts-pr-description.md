# Suggested PR title

feat(accounts): email accounts, wallet assignment and role-based management

## Summary

- Add email registration/login, backend sessions and per-user wallet assignment.
- Add profile/password settings, logout, wallet ID display and app-account closure.
- Let administrators create users, change roles and close accounts.
- Enforce permissions in APIs, page routes and navigation; revoke sessions on password,
  role or account-status changes.
- Resolve audit actors from the signed-in session while keeping platform-signed audit writes.
- Integrate main through PR #30, preserving the real Policies/Approvals pages and latest fixes.
- Preserve main's migrations V1-V5 and add Accounts as V6-V8.

## Verification

- Full backend test suite, including account lifecycle/role tests and authenticated policy APIs.
- Migration test: main V5 database upgrades to V8 without losing existing policy balances.
- Frontend account/client tests, production build and ESLint.
- Tests use H2/mocks; no live Hedera transactions were run during integration.

## Important review notes

- Old Accounts-only databases with Accounts migrations at V4/V5/V6 need a reviewed history
  reconciliation before using this branch. No existing database was modified.
  See accounts-main-integration.md. Do not reset databases containing real wallet keys.
- Closing an app account retains encrypted wallet keys and does not delete its Hedera account.
- Seeded demo users, legacy password hashing and in-memory sessions are development-only;
  production authentication/security hardening remains out of scope.
- No email verification or password recovery yet; displayed balances are stored values.
- Previously reported audit-verification and policy atomicity/concurrency defects remain
  separate work, not fixes included in this PR.
