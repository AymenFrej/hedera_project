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
  "actorType": "USER",
  "actorId": "user_42",
  "actorHederaAccountId": "0.0.777",
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

### What the topic itself guarantees

The audit topic is created with two permanent properties:

| Property | Effect |
|---|---|
| **No admin key** | Nobody can delete the topic — including us. HCS messages can never be edited, but a topic *with* an admin key can be deleted by whoever holds it. |
| **A submit key** | Only the platform can append. Without one, anyone on the network could post forged audit events into the topic. |

Both are fixed at creation and cannot be changed afterwards, which is the point. A topic created
without them cannot be repaired — create a new one.

The application does not take this on faith: at startup it asks the Mirror Node what the configured
topic actually looks like and logs a warning when a guarantee is missing. `AuditTopicHardeningLiveIT`
asserts the same thing against the real network.

```
Audit topic 0.0.10684842 verified: no admin key (nobody can delete it)
and a submit key (only this platform can append).
```

### Attribution: who performed the action

Audit events carry an actor. Two rules decide the design:

1. **The actor is resolved server-side, never sent by the client.** `POST /api/v1/audit` has no
   actor field; anything an attacker puts in the body is ignored. A trail whose subject is chosen
   by the caller proves nothing. This is covered by `AuditControllerActorTest`.
2. **The platform signs and pays for every audit message.** The actor goes into the message
   *content*, not into the transaction payer. If the audited party signed their own trail, they
   could suppress it by refusing to sign or by running out of HBAR — the same reason an employee
   does not write their own entry in an audit log.

```json
"actor": { "type": "USER", "id": "user_42", "hederaAccountId": "0.0.777" }
```

`type` is `USER`, `AGENT` or `SYSTEM`. `hederaAccountId` is null when the actor has no account.

Until authentication exists, everything is attributed to `SYSTEM / platform`. No user is invented:
writing a false actor to an immutable ledger is worse than writing none.

#### Plugging in authentication (Accounts module)

Declare an `ActorResolver` bean; it replaces the default automatically.

```java
@Component
public class SessionActorResolver implements ActorResolver {
    @Override
    public Actor currentActor() {
        var user = /* read from the session, not from request parameters */;
        return Actor.user(user.id(), user.hederaAccountId());
    }
}
```

The user's wallet is still useful — for payments and token transfers, where the user *should* be
the payer. It is only the audit trail that must stay platform-signed.

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
