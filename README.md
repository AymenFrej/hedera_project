# Hedera Agent Platform

## Accounts integration

This branch adds email login, wallet assignment, profile/password settings, role-based navigation
and administrator user management. Policies and Approvals from main remain available to authorized
roles. Start at /login; the protected pages now require a running backend.

Before using an existing Accounts-branch database, read
[the migration compatibility notes](docs/accounts-main-integration.md). Main's V1-V5 migrations
are preserved; Accounts uses V6-V8. Older Accounts databases need a separately reviewed history
upgrade; do not reset them or change the wallet-key encryption secret.

See [account permissions and tests](docs/account-access.md). The starter overview below describes
the original template; real account creation and HCS writes can now occur when Hedera credentials
are configured. Treat seeded demo users and authentication as development-only, not production-ready.

## Team demo logins (local development only)

Open [http://localhost:5173/login](http://localhost:5173/login) after starting the backend and
frontend. On a fresh database, Flyway V7 creates these accounts:

| Role | Email | Password | What to test |
| --- | --- | --- | --- |
| ADMIN | admin@example.com | `AdminPass123!` | Manage users, roles, account restoration, policies, approvals and audit |
| USER | user@example.com | `UserPass123!` | Own wallet, profile/password settings; Payments/Tokens placeholders |
| AUDITOR | auditor@example.com | `AuditorPass123!` | Own settings and read-only audit |
| PLATFORM | platform@example.com | `PlatformPass123!` | User management, policies, approvals and audit operations |

These are intentionally public **demo passwords**, not production credentials. Do not expose
this seeded application publicly or reuse these passwords elsewhere. Existing databases retain
any passwords or roles already changed; pulling code does not reset them.

The four seeded wallets are mocks (`0.0.demo-*`). Configuring Hedera does not automatically
replace them. With valid **testnet** credentials, new registrations/admin-created users get
real wallets. An administrator can use **Create real wallet** to upgrade an active mock account.
Keep operator credentials and the wallet-key encryption secret in your private environment,
never in Git. Do not rotate the encryption secret without a wallet-key migration.

Quick manual check: log in as ADMIN, create a disposable user, edit it, change its role,
close it and restore it. Use **Delete mock account** only for a closed mock account without
stored keys. Real wallets can be closed/restored but their keys are never purged by CRUD.
Then log in as each role to check the sidebar and denied routes. Password changes and role
changes invalidate old sessions; backend restarts require signing in again.

API calls other than login/register/health require `Authorization: Bearer <token>`, using the
token returned by `POST /api/v1/auth/login`. Policy demos require ADMIN or PLATFORM.
The default backend URL is `http://localhost:8080/api/v1`; if using another port, set
`VITE_API_BASE_URL` in `frontend/.env.local` and restart Vite. A developer's existing local
override (for example port 8091) is not committed and should not be assumed on teammates' machines.

Before updating an old Accounts database, read [the migration notes](docs/accounts-main-integration.md).
Do not delete database volumes to resolve migration errors: they may contain real wallet keys.

> **This is a starter template.** Most Hedera, AI, and business behavior is intentionally mocked so five developers can work in parallel on complete vertical features.

## Project idea

The platform will let a business use specialized agents for Hedera operations without needing to know Hedera internals. Each capability owns its agent, backend service/API, frontend experience, tests, and Hedera adapter.

## Architecture

This is a modular monolith: one Spring Boot backend and one React frontend, split into independently developable feature modules. Shared agent models are deliberately small. Each module has an interface-first Hedera gateway and a mock implementation that can later be replaced by the Hedera Java SDK.

## Stack

- React, TypeScript, Vite, React Router, Lucide
- Java 21 target, Spring Boot, Maven, Spring Data JPA, Flyway
- PostgreSQL 16 via Docker Compose
- Hedera integration extension points only (no real transactions)

## Repository layout

```text
backend/src/main/java/com/hedera/agentplatform/
  accounts/ payments/ tokens/ audit/ policies/
  shared/ (agent contract, models, config, errors)
frontend/src/
  modules/ (feature space reserved for future growth)
  components/ layout/ pages/ mocks/ types/
docs/ (architecture, ownership, contracts, development guide)
```

## Run it

1. Copy `.env.example` to `.env` if you want local overrides.
2. Start PostgreSQL: `docker compose up -d postgres`.
3. Start the backend from `backend`: `mvnw.cmd spring-boot:run` on Windows or `./mvnw spring-boot:run` on macOS/Linux. It listens on `http://localhost:8080`.
4. Start the frontend from `frontend`: `npm install`, then `npm run dev`. It listens on `http://localhost:5173`.

The frontend is designed to run from mock JSON without the backend. Backend GET endpoints return a small mock record and `/api/v1/health` returns `{ "status": "UP" }`.

## Team ownership

See [docs/team-ownership.md](docs/team-ownership.md). Each owner can work inside their feature folder without waiting for another module. Shared contracts should change through a reviewed pull request.

## Adding a feature

Start with the module's agent intent and API contract, implement service/repository behavior behind the existing controller, replace the mock Hedera gateway with an adapter, add tests, then connect the page's mock data/API client. Keep module-specific code inside its module folder.

## Git workflow

Use `main` as the integration branch and open pull requests from feature branches such as `feature/accounts-create`, `feature/payments-transfer`, `feature/tokens-mint`, `feature/audit-hcs`, and `feature/policies-limits`.

## Environment variables

See `.env.example`. Never commit operator IDs, private keys, or real secrets. The current defaults target Hedera testnet and a local PostgreSQL instance.

## Current status

The dashboard, routes, placeholder pages, mock data, controller surfaces, shared agent contract, database schema, documentation, and mock Hedera gateways are ready. Real AI/LLM orchestration, authentication, key management, transactions, USDC, HCS, Mirror Node queries, and production security are intentionally out of scope for this template.
