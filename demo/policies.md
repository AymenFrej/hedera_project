# Demo: the agent is not allowed to decide alone

The point is not that the agent moves money. It is that a deterministic rule engine settles every
request first, and that a request which cannot settle is never put in front of a human.

## Why this matters

An agent driven by a language model can be argued with. Ask it the right way and it releases the
rent. So the model never decides here: it may phrase a request, but a pure function of
(request, balances, rules) returns ALLOW / HOLD / DENY, and that decision — not the transfer — is
what gets written to the audit trail.

Three things follow, and the run below shows all three:

- **DENY beats HOLD.** What cannot settle is never offered for approval. A human cannot approve
  their way past insufficient funds.
- **Every verdict carries a stable rule id** (`funds.insufficient`, `emergency.human`) plus a
  sentence a non-engineer can read.
- **Emergency money always goes to a human**, however small the amount.

## Setup

```bash
# 1. Postgres
docker compose up -d postgres        # or a local Postgres on 5432

# 2. Backend
cd backend && ./mvnw spring-boot:run

# 3. Frontend
cd frontend && npm run dev           # http://localhost:5173/policies
```

Hedera credentials are optional for this demo. Without them decisions are still recorded, with
`anchorStatus=PENDING` instead of `ANCHORED` — see `demo/README.md` for the ledger half.

**Sign in first.** Every `/api/v1/**` call except health, login and register now goes through the
Accounts interceptor, and the policy API is limited to two roles. Log in at
[http://localhost:5173/login](http://localhost:5173/login) as `admin@example.com` /
`AdminPass123!` (a seeded, development-only password). Measured on a running backend:

| Role | `GET /api/v1/policies/state` |
|---|---|
| ADMIN, PLATFORM | 200 |
| USER, AUDITOR | 403 |
| no token at all | 401 |

## The run, in five steps

Sign in as ADMIN, then click **Reset the demo** first. The envelopes open at rent 500 /
essentials 300 / emergency 200, and `landlord-tunis` is the one counterparty already paid before.
(`/policies` and `/approvals` are hidden from USER and AUDITOR — signing in as the wrong role is
the usual reason the pages look empty.)

| # | Do this on `/policies` | What the audience sees |
|---|---|---|
| 1 | Spend **100** from **RENT** to `landlord-tunis` | Green **ALLOWED**, rule `policy.ok`. The rent card drops 500 → 400 on the spot |
| 2 | Spend **9999** from **RENT** to `landlord-tunis` | Red **DENIED**, rule `funds.insufficient`, *"requested 9999 but rent holds 400"*. No approval is created |
| 3 | Spend **50** from **EMERGENCY** to `landlord-tunis` | Amber **HELD FOR A HUMAN**, rule `emergency.human`. An approval id appears — the same counterparty that was waved through in step 1 |
| 4 | Open `/approvals`, click **Approve** | The row becomes `APPROVED by platform`. Back on `/policies`, emergency reads 150 |
| 5 | Click **Approve** on that same row again | `409` — *"was already approved"*. A question gets answered once |

Step 2 is the one to slow down on: the same human who approves step 3 is given **no button** in
step 2. The refusal is not a permission level, it is arithmetic.

## What to say

> The agent asked to spend. It did not decide. Rent went through because it fit the envelope and
> the payee was known. The 9999 was refused outright — nobody was asked, because no approval makes
> that amount exist. The emergency 50 stopped and waited for a person, and that person's answer is
> itself on the audit trail next to the decision that raised it.

## The same run, from a terminal

Useful when the projector dies, and it is how the numbers in the table above were produced.

```bash
API=http://localhost:8080/api/v1/policies

# Log in first: without this every call below answers 401.
TOKEN=$(curl -s -X POST http://localhost:8080/api/v1/auth/login \
  -H 'Content-Type: application/json' \
  -d '{"email":"admin@example.com","password":"AdminPass123!"}' \
  | sed -E 's/.*"token":"([^"]+)".*/\1/')
AUTH="Authorization: Bearer $TOKEN"

curl -s -X POST $API/demo/reset -H "$AUTH"
curl -s -X POST $API/decide -H "$AUTH" -H 'Content-Type: application/json' \
  -d '{"envelope":"RENT","amount":100,"counterparty":"landlord-tunis"}'
curl -s -X POST $API/decide -H "$AUTH" -H 'Content-Type: application/json' \
  -d '{"envelope":"RENT","amount":9999,"counterparty":"landlord-tunis"}'
curl -s -X POST $API/decide -H "$AUTH" -H 'Content-Type: application/json' \
  -d '{"envelope":"EMERGENCY","amount":50,"counterparty":"landlord-tunis"}'
# take approvalId from that last answer
curl -s -X POST $API/approvals/<approvalId>/approve -H "$AUTH"
curl -s $API/state -H "$AUTH"
```

Step 3 answers with:

```json
{"verdict":"HOLD","ruleId":"emergency.human",
 "reason":"emergency funds always require human approval",
 "balanceAfter":150,"approvalId":"approval_...","auditEventId":"audit_...","anchored":false}
```

`GET /api/v1/policies` lists all nine rules with their verdicts, so a judge can read the policy
before seeing it applied rather than inferring it from one verdict after.

## Two questions a judge asks

**"Could the model have talked its way past that?"** The verdict never passes through a model.
`PolicyEngine.decide` is a pure static function; the 91-test backend suite runs it offline with no
network and no SDK.

**"What if I run the demo twice?"** **Reset the demo** puts the envelopes back and clears the
queue. It deliberately does *not* touch the audit trail — a history you can wipe proves nothing, so
the reset shows up in it too.
