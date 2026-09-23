# API contracts

Most endpoints are still GET-only placeholders returning mock records. The audit module is the
exception: it writes to Hedera and can prove it. IDs are stable-shaped strings so clients can be
built before persistence is complete.

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

Audit events carry the proof returned by Hedera. `anchorStatus` says whether the ledger fields mean
anything: `PENDING` (local only), `ANCHORED` (accepted by the network), `FAILED` (submission
attempted and refused).

```json
{
  "id": "audit_123",
  "agent": "PaymentAgent",
  "action": "TRANSFER",
  "status": "SUCCESS",
  "createdAt": "2026-09-23T15:44:25Z",
  "anchorStatus": "ANCHORED",
  "topicId": "0.0.10683636",
  "transactionId": "0.0.10682427@1790178255.012000185",
  "consensusTimestamp": "2026-09-23T15:44:25.660169838Z",
  "sequenceNumber": 2,
  "payloadHash": "sha256 hex of the submitted message"
}
```

### Audit routes

| Method | Route | Purpose |
|---|---|---|
| `GET` | `/api/v1/audit` | list events, newest first |
| `GET` | `/api/v1/audit/{id}` | one event |
| `POST` | `/api/v1/audit` | record an agent action and anchor it to HCS |
| `GET` | `/api/v1/audit/{id}/verification` | read the event back from the Mirror Node and compare hashes |
| `GET` | `/api/v1/audit/status` | `{ "ledgerActive": true }` when credentials are configured |

`POST /api/v1/audit` body:

```json
{ "agent": "PaymentAgent", "action": "TRANSFER", "status": "SUCCESS", "metadata": { "amount": "50" } }
```

Verification response:

```json
{
  "verified": true,
  "detail": "Ledger message matches the stored payload (SHA-256 ...)",
  "ledgerPayload": "{...}",
  "consensusTimestamp": "1790178265.660169838",
  "explorerUrl": "https://hashscan.io/testnet/topic/0.0.10683636"
}
```

**Writes go through the SDK, reads go through the Mirror Node.** Free SDK queries are throttled on
testnet and answer `BUSY`; reading the proof back through a different channel than the one that
wrote it is also what makes the verification worth something.

### Emitting audit events from another module

Inject `AuditService` and call `record(...)`. The event is stored locally, submitted to HCS, and
becomes verifiable:

```java
auditService.record("PaymentAgent", "TRANSFER", "SUCCESS", Map.of("amount", "50"));
```

Without operator credentials the call still works: the event is stored with `anchorStatus=PENDING`
and nothing is sent to the network, so you can develop without a testnet account.

## Routes

`GET /api/v1/health`, `/accounts`, `/payments`, `/tokens`, `/audit`, `/policies`, and
`/policies/approvals` are available. The shared Java contract is `AgentCapability`: `supports`,
`plan`, and `execute`.
