# Team ownership

Each person owns a complete vertical: agent, backend, API, persistence, frontend, tests, and Hedera adapter. The folders below are starting boundaries, not separate services.

## Person 1 — Accounts

- Backend: `backend/src/main/java/com/hedera/agentplatform/accounts/`
- Frontend: `frontend/src/pages/FeaturePage.tsx` (accounts route), future `frontend/src/modules/accounts/`
- Agent: `accounts/agent/AccountAgent.java`
- API: `/api/v1/accounts`
- Tests: `backend/src/test/.../accounts`, frontend accounts tests
- Hedera: `HederaAccountGateway`, key-management abstraction, account/balance operations

## Person 2 — Payments

- Backend: `backend/src/main/java/com/hedera/agentplatform/payments/`
- Frontend: payments route, future `frontend/src/modules/payments/`
- Agent: `payments/agent/PaymentAgent.java`
- API: `/api/v1/payments`
- Tests: payments service, API, and gateway tests
- Hedera: `HederaPaymentGateway` for HBAR and HTS transfers, status and treasury workflows

## Person 3 — Tokens

- Backend: `backend/src/main/java/com/hedera/agentplatform/tokens/`
- Frontend: tokens route, future `frontend/src/modules/tokens/`
- Agent: `tokens/agent/TokenAgent.java`
- API: `/api/v1/tokens`
- Tests: token lifecycle and gateway tests
- Hedera: `HederaTokenGateway` for create/mint/burn/transfer/association; future NFT, KYC, freeze, pause, fees

## Person 4 — Audit & Monitoring

- Backend: `backend/src/main/java/com/hedera/agentplatform/audit/`
- Frontend: audit route, future `frontend/src/modules/audit/`
- Agents: `MonitoringAgent` and `AuditAgent`
- API: `/api/v1/audit`
- Tests: event publishing, verification, monitoring, and alert tests
- Hedera: `HederaAuditGateway` for future HCS logging and Mirror Node queries; every module should emit `AuditEvent`

## Person 5 — Policies & Approvals

- Backend: `backend/src/main/java/com/hedera/agentplatform/policies/`
- Frontend: policies and approvals routes, future `frontend/src/modules/policies/`
- Agent: `policies/agent/PolicyAgent.java`
- API: `/api/v1/policies` and `/api/v1/policies/approvals`
- Tests: policy evaluation, limits, approvals, and conditional execution
- Hedera: coordinate with payment/token owners; no direct adapter is required in the starter
