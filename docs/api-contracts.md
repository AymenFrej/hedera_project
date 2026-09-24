# API contracts

Most endpoints are still GET-only placeholders returning mock records. The audit and payments
modules are the exceptions: they write to Hedera. IDs are stable-shaped strings so clients can be
built before persistence is complete.

## Account

```json
{ "id": "acct_123", "hederaAccountId": "0.0.123", "balance": "10", "status": "ACTIVE" }
```

## Payment

Every payment goes through the same path, whether it comes from the UI or from `PaymentAgent`:

```
request → policy (ALLOW / HOLD / DENY) → [human approval if HOLD] → Hedera transfer → audit (HCS)
```

| Status | Meaning |
|---|---|
| `PENDING` | recorded, not yet checked |
| `AWAITING_APPROVAL` | policy said HOLD; waits for `approve` or `reject` |
| `REJECTED` | policy said DENY, or a human rejected it; nothing was sent |
| `SUBMITTED` | sent to Hedera, receipt not back yet |
| `CONFIRMED` | Hedera accepted the transfer |
| `FAILED` | Hedera refused it; `failureReason` holds the receipt status |
| `SIMULATED` | no Hedera credentials: policy and audit ran, nothing was transferred |

```json
{
  "id": "pay_7b1e…",
  "amount": "12.5",
  "currency": "HBAR",
  "tokenId": null,
  "destination": "0.0.4242",
  "envelope": "ESSENTIALS",
  "memo": "rent share",
  "status": "CONFIRMED",
  "sourceAccount": "0.0.10682427",
  "transactionId": "0.0.10682427@1790178255.012000185",
  "explorerUrl": "https://hashscan.io/testnet/transaction/0.0.10682427@1790178255.012000185",
  "policyVerdict": "ALLOW",
  "policyRuleId": "policy.ok",
  "policyReason": "within essentials envelope and under limits",
  "failureReason": null,
  "requestedByType": "SYSTEM",
  "requestedById": "platform",
  "createdAt": "2026-09-23T19:30:00Z",
  "updatedAt": "2026-09-23T19:30:04Z"
}
```

### Payment routes

| Method | Route | Purpose |
|---|---|---|
| `GET` | `/api/v1/payments` | list payments, newest first |
| `GET` | `/api/v1/payments/{id}` | one payment |
| `POST` | `/api/v1/payments/preview` | what executing would do: policy verdict + ledger checks; records and sends nothing |
| `POST` | `/api/v1/payments` | check policy, then send or hold |
| `POST` | `/api/v1/payments/{id}/approve` | send a held payment (`409` if it is not `AWAITING_APPROVAL`) |
| `POST` | `/api/v1/payments/{id}/reject` | refuse a held payment |
| `GET` | `/api/v1/payments/{id}/verification` | read the transfer back from the Mirror Node and compare it field by field |
| `GET` | `/api/v1/payments/{id}/result` | the result screen: outcome, Mirror Node check, timeline from the payment's audit events, each event verified on HCS |
| `GET` | `/api/v1/payments/{id}/audit` | the audit events this payment wrote |
| `GET` | `/api/v1/payments/{id}/audit/{eventId}/verification` | read one of them back from HCS (through the audit module) |
| `GET` | `/api/v1/payments/balance` | balances of the paying account (HBAR + associated tokens), from the Mirror Node |
| `GET` | `/api/v1/payments/status` | `{ "ledgerActive": true, "demoTokenId": "0.0.…", "demoRecipientId": "0.0.…" }` |

`POST /api/v1/payments` body. Omit `tokenId` for HBAR. `amount` is what a person types: HBAR accepts
up to 8 decimals (1 tinybar), a token up to its own decimals, read once from the Mirror Node
(`1.5` of a 6-decimal token is sent as 1,500,000). Anything finer is refused rather than rounded; an
unknown token is refused before anything is sent; `503` when the Mirror Node cannot be asked.

```json
{ "destination": "0.0.4242", "amount": "12.5", "tokenId": null, "envelope": "ESSENTIALS", "memo": "rent share" }
```

Like audit, the request has no "who" field: the requester comes from `ActorResolver`.

**Idempotency.** Send an `Idempotency-Key` header (8–64 characters: letters, digits, `-`, `_`,
`:`) that identifies one payment attempt. Repeating the request with the same key (double click,
retry after a network error) returns the payment already created instead of paying twice; reusing
the key for a different payment is refused with `409`. The Payments page sends a new key per
attempt, and `PaymentAgent` uses the agent `requestId`, so an orchestrator retry never pays twice.
Requests without the header are not deduplicated.

### Preview → Execute

`POST /payments/preview` takes the same body as `POST /payments` and answers what executing it now
would do. **Nothing is recorded, sent or audited, and it authorizes nothing**: Execute is a normal
`POST /payments`, which asks the policy again and follows its new answer if it changed.

Two kinds of information, kept apart:

- `policy`: the verdict exactly as `PaymentPolicy` returned it (`verdict`, `ruleId`, `reason`).
  Payments invents no rule. `PaymentPolicy.evaluate` must therefore be free of side effects.
- `checks`: facts read from the Mirror Node, each `PASS`, `WARN`, `FAIL` or `UNKNOWN`: recipient
  exists, token exists, recipient can receive the token (associated, or accepts automatic
  associations: an unassociated account with `max_automatic_token_associations = -1` is *not* a
  failure), and the paying account's balance.

```json
{
  "outcome": "READY",
  "summary": "Policy allows it and the ledger checks pass",
  "payerAccount": "0.0.5239440", "destination": "0.0.10687139",
  "amount": "5", "amountUnits": 5, "tokenId": "0.0.10687138", "symbol": "PAYTEST",
  "balanceBefore": "999950", "balanceAfter": "999945",
  "policy": { "verdict": "ALLOW", "ruleId": "policy.none", "reason": "no policy engine connected yet: payment not checked" },
  "checks": [
    { "name": "Recipient", "status": "PASS", "detail": "0.0.10687139 exists on testnet" },
    { "name": "Token", "status": "PASS", "detail": "PAYTEST (0.0.10687138), 0 decimals" },
    { "name": "Recipient can receive PAYTEST", "status": "PASS", "detail": "Associated with 0.0.10687138" },
    { "name": "Balance", "status": "PASS", "detail": "999950 PAYTEST available" }
  ],
  "note": "Preview only: nothing was recorded or sent. Execute checks the policy again."
}
```

| outcome | meaning |
|---|---|
| `BLOCKED` | policy DENY: no Hedera transaction would be created (the page offers no Execute) |
| `LIKELY_TO_FAIL` | a ledger check says Hedera would refuse it, and the network fee would still be charged |
| `NEEDS_APPROVAL` | policy HOLD: it would wait for a human before anything is sent |
| `READY` | policy allows it and the ledger checks pass |
| `SIMULATION` | no Hedera credentials: nothing to check, nothing would be transferred |

`balanceAfter` is null when the balance is not enough; for HBAR the network fee comes on top.

### Result

`GET /payments/{id}/result` gathers what happened to one payment. Nothing in it is a fixed script:

- `outcome`: `CONFIRMED`, `FAILED` (sent; Hedera refused it or it never reached consensus),
  `BLOCKED` (policy DENY, never sent), `REJECTED` (a reviewer refused it, never sent),
  `AWAITING_APPROVAL`, `IN_PROGRESS` (sent, no final result yet; checking again settles it),
  `SIMULATED`. **BLOCKED is not FAILED**: a blocked payment never reached Hedera.
- `timeline`: the payment record, then one step per audit event the payment wrote, found by the
  `paymentId` inside the payload anchored to HCS. A step exists only because its event exists; the
  only derived steps are facts about the record ("No Hedera transaction was created" when there is
  no transaction id, "Waiting for a reviewer", "Waiting for the Hedera receipt").
- `ledger`: the Mirror Node comparison, `null` when no transaction was created.
- `audit`: each event with its anchoring, read back from HCS when anchored.
- `badges`: `policyChecked` (a real policy decided, not `policy.none`), `ledgerVerified` (confirmed
  and matching on the Mirror Node), `auditVerified` (every event read back from HCS and matching).
  A badge is false unless its check just passed.

### Verification: the Mirror Node is the source of truth

The transaction id is generated and stored **before** the transfer is sent, so a payment always
knows which ledger transaction to look for. `GET /payments/{id}/verification` reads it back from
the Mirror Node and compares result, recipient and sender with the record:

```json
{
  "verified": true,
  "detail": "Ledger transaction matches the payment",
  "paymentStatus": "CONFIRMED",
  "transactionId": "0.0.5239440@1790194819.526000707",
  "ledgerResult": "SUCCESS",
  "consensusTimestamp": "1790194854.872086514",
  "checks": [
    { "name": "Network result", "expected": "SUCCESS", "actual": "SUCCESS", "ok": true },
    { "name": "Recipient received", "expected": "0.01 ℏ to 0.0.10682427", "actual": "0.01 ℏ", "ok": true },
    { "name": "Sender paid", "expected": "at least 0.01 ℏ from 0.0.5239440", "actual": "0.01128158 ℏ (fee included)", "ok": true }
  ],
  "explorerUrl": "https://hashscan.io/testnet/transaction/0.0.5239440@1790194819.526000707"
}
```

- A **failed** payment verifies when the ledger agrees it failed and nothing reached the recipient.
  A refused transfer still reaches consensus, so its network fee is charged.
- A **blocked** payment (policy DENY, or rejected) has no transaction: *"Blocked before reaching
  Hedera: no transaction was created"*.
- **Recovery.** A timeout does not mean a transfer failed, so a payment with no receipt stays
  `SUBMITTED`. Once it is older than 5 minutes (SDK retries plus the transaction's validity
  window), verification settles it from the ledger: `CONFIRMED` or `FAILED` if the Mirror Node has
  it, `FAILED` ("never reached consensus") if it does not. The same runs at startup for payments
  left `SUBMITTED` before a restart. Nothing is concluded while the Mirror Node is unreachable.
  Each settlement is audited as `TRANSFER_SETTLED`.

### Balance

Facts only, no limits or verdicts: the policy decides what a balance means. Each asset carries
both the smallest unit (`units`, what the policy engine compares) and the decimal `amount` (what a
person reads). `available` is false in simulation mode or when the Mirror Node is unreachable, with
`detail` saying which.

```json
{
  "available": true,
  "detail": null,
  "account": "0.0.5239440",
  "hbar": { "tokenId": null, "symbol": "HBAR", "name": "HBAR", "decimals": 8, "units": 99967250794, "amount": "999.67250794" },
  "tokens": [ { "tokenId": "0.0.7777", "symbol": "USDC", "name": "USD Coin", "decimals": 6, "units": 5000000, "amount": "5.000000" } ],
  "asOf": "2026-09-23T20:37:16.839746104Z",
  "explorerUrl": "https://hashscan.io/testnet/account/0.0.5239440"
}
```

The account is whichever one payments leave from (`PaymentSigner.payer`), so it becomes the
signed-in user's wallet once Accounts provides a signer.

### Demo token and live tests

`PAYMENT_DEMO_TOKEN_ID` / `PAYMENT_DEMO_RECIPIENT_ID` name a fixed-supply HTS token (**0 decimals**,
so an amount in smallest units is the amount a person types) whose treasury is the operator, and a
recipient already associated with it. Create them once, then copy the printed ids into `.env`:

```bash
cd backend && ./mvnw test -Dtest=PaymentDemoSetup
```

`PaymentTokenLiveIT` creates its own token and recipients on every run and checks, on testnet and
through the Mirror Node: a transfer to an associated account (`CONFIRMED`), to a non-associated one
(`TOKEN_NOT_ASSOCIATED_TO_ACCOUNT`), more than the balance (`INSUFFICIENT_TOKEN_BALANCE`), and an
invalid token or recipient (`INVALID_TOKEN_ID` / `INVALID_ACCOUNT_ID`). It takes several minutes.

```bash
cd backend && ./mvnw test -Dtest=PaymentTokenLiveIT
```

For an HTS transfer the recipient must already be **associated** with the token, otherwise Hedera
answers `TOKEN_NOT_ASSOCIATED_TO_ACCOUNT` and the payment ends `FAILED`.

### Audit events written by Payments

| action | status | when |
|---|---|---|
| `PAYMENT_POLICY` | `ALLOW` / `HOLD` / `DENY` | right after the policy decision |
| `PAYMENT_APPROVAL` | `APPROVED` / `REJECTED` | a human decided on a held payment |
| `TRANSFER` | `SUCCESS` / `FAILED` / `UNKNOWN` | after the Hedera receipt (`UNKNOWN`: no receipt, e.g. timeout) |
| `TRANSFER_SETTLED` | `CONFIRMED` / `FAILED` | a `SUBMITTED` payment settled from the Mirror Node |

Metadata carries `paymentId`, `amount`, `currency`, `destination`, and when known `envelope`,
`policyRuleId`, `transactionId`, `failureReason`.

### Extension points for other modules

Both follow the `ActorResolver` pattern: declare a bean and it replaces the default.

**Policies: `PaymentPolicy` → `EnginePaymentPolicy`.** Everything is the Policies module's; Payments
only turns a payment into a `PolicyRequest` (envelope, amount in the payment's smallest unit,
recipient account as counterparty):

| Payments step | Policies module |
|---|---|
| Preview | `PolicyEngine.decide()` on `EnvelopeLedger.state()`: pure, nothing recorded or debited |
| Execute | `ApprovalService.submit()`: decision recorded as `POLICY_DECISION`, envelope debited on ALLOW, approval opened on HOLD |
| Approve (Payments or `/policies/approvals`) | `ApprovalService.approve()`: re-checks affordability, debits, records `POLICY_APPROVAL` |
| Reject | `ApprovalService.reject()`, recorded as `POLICY_APPROVAL` |

The payment stores `policyAuditEventId` and `approvalId` (V9), so its result timeline shows the
Policies module's decision and approval events next to its own. A held payment can be answered in
either place: approved in the queue, it is sent when approved in Payments; rejected there, it can
no longer be sent. Decisions and the ledger are serialized in Payments, so concurrent payments
cannot together overspend an envelope.

Known gaps, for the Policies module: envelopes have no asset or unit (the demo runway 500/300/200
fits PAYTEST, 0 decimals, not HBAR tinybars), and an envelope debited at decision time is not
credited back when the Hedera transfer then fails.

**Accounts — `PaymentSigner`.** Until one exists, `OperatorPaymentSigner` sends from the platform
operator. A custodial signer returns the signed-in user's account from `payer(...)` and, in
`prepare(...)`, sets the transaction id to that account, freezes the transaction and signs it with
the user's key. Audit messages are not affected: they stay platform-signed.

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
