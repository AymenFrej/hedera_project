# API contracts

All endpoints are currently GET-only placeholders and return mock records. IDs are stable-shaped strings so clients can be built before persistence is complete.

## Account

```json
{ "id": "acct_123", "hederaAccountId": "0.0.123", "balance": "10", "status": "ACTIVE" }
```

## Payment

```json
{ "id": "pay_123", "amount": "50", "currency": "HBAR", "destination": "0.0.123", "status": "CONFIRMED" }
```

## Agent task

```json
{ "taskId": "task_123", "agent": "PaymentAgent", "status": "READY", "actions": [] }
```

## Audit event

```json
{ "id": "audit_123", "agent": "PaymentAgent", "action": "TRANSFER", "status": "SUCCESS" }
```

## Routes

`GET /api/v1/health`, `/accounts`, `/payments`, `/tokens`, `/audit`, `/policies`, and `/policies/approvals` are available. The shared Java contract is `AgentCapability`: `supports`, `plan`, and `execute`.
