# Accounts branch integration

The Accounts branch includes main through PR #26 and preserves its Policies, Approvals,
audit APIs, error messages and styles. Login is now required for the protected UI and APIs.
See [account-access.md](account-access.md) for the role matrix and account lifecycle.

## Database migration order

Main's applied migrations V1 through V5 are unchanged. Accounts now adds:

- V6__accounts_users.sql
- V7__seed_demo_roles.sql
- V8__encrypt_account_keys.sql

A fresh database or a database already running main through V5 can migrate normally.
The migration integration test verifies the V5-to-V8 upgrade and preservation of existing
policy balances. Seeded identities are development-only and must not be deployed unchanged
to a public environment.

### Existing databases from the old Accounts branch

STOP before starting the new backend against these databases. That branch previously used
V4__accounts_users.sql, V5__seed_demo_roles.sql and V6__encrypt_account_keys.sql. Its recorded
Flyway history is incompatible with the newly combined order. A failed validation is expected;
do not disable validation, run a blanket repair, reset the database, or delete wallet rows.

No existing database or its history was changed while preparing this branch.
Before upgrading an old Accounts database:

1. Back up the complete PostgreSQL database and verify the restore in isolation.
2. Securely preserve the existing account-key encryption secret (do not rotate it).
3. Inspect the restored schema and flyway_schema_history to confirm exactly which migrations ran.
4. On the restored copy, design a reviewed history reconciliation mapping the original Accounts
   migrations to V6/V7/V8, preserving their original content/checksums, and apply the missing
   main V4/V5 policy migrations with an explicitly controlled out-of-order upgrade.
5. Validate migration history, account and encrypted-key row counts, login, roles, policy data,
   and wallet references before repeating an approved procedure on the real database.

This is deliberately not an automatic repair script: modifying migration history against an
unverified database can destroy or strand real wallet access. Existing installations need
their own reviewed upgrade plan. A new, separately named development database is another
option, but it will not contain the old accounts or wallets.

## Verification

From backend: mvnw.cmd clean test (or ./mvnw clean test).
From frontend: npm test, npm run build, npm run lint.

Policy endpoint tests now authenticate with a real test session rather than bypassing
authorization. Human approvals record the signed-in actor instead of SYSTEM/platform.
H2/mocks are used for tests; no live Hedera transactions are required.

## PR scope and known limitations

This PR provides email login/registration, wallet assignment, account settings, administrative
user management, session-backed role restrictions, actor attribution and integration with main.
It does not provide email verification/password recovery or complete autonomous agents.
Wallet balances displayed by Accounts are stored balances, not live network balances.
App closure disables access while retaining the on-chain wallet and encrypted keys.
Passwords still use the inherited development hashing scheme; production hardening remains.

The previously reviewed policy settlement concurrency/atomicity, audit payload verification,
demo reset auditing and stale balance-card issues are not changed by this integration.
