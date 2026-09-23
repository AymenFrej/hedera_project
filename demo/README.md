# Demo: proving the audit trail cannot be rewritten

The point is not that verification says OK. It is that verification says **NO** when someone
changes the data.

## Why this matters

A database can be edited by anyone with access to it — an administrator, a compromised account, a
bad migration. Nothing in the database itself reveals the change: update the row, recompute its
hash, and the record looks perfectly consistent.

The ledger is the one thing that cannot be rewritten. Comparing the two is what turns a record
into evidence.

## Setup

```bash
# 1. Postgres
docker compose up -d postgres        # or a local Postgres on 5432

# 2. Backend, with Hedera credentials
export HEDERA_OPERATOR_ID=0.0.xxxxx
export HEDERA_OPERATOR_PRIVATE_KEY=0x...
export HEDERA_AUDIT_TOPIC_ID=0.0.10684842
cd backend && ./mvnw spring-boot:run

# 3. Frontend
cd frontend && npm run dev           # http://localhost:5173/audit
```

The `pgcrypto` extension is needed for the SQL script:

```sql
CREATE EXTENSION IF NOT EXISTS pgcrypto;
```

## The run, in four steps

| # | Do this | What the audience sees |
|---|---|---|
| 1 | Open `/audit`, click **Record test event** | A new row, badge `ANCHORED`, with its transaction id and consensus timestamp |
| 2 | Click **Verify** | Green: *Verified against the ledger* |
| 3 | In a terminal, run `psql ... -f demo/tamper.sql` | The amount changes from 50 to 5000 **in the database only** |
| 4 | Click **Verify** again | Red: *Tampering detected*, with both hashes side by side — ours vs the ledger's |

Step 3 in front of the audience matters: they see a plain `UPDATE`, not a button we wrote. Then
open the HashScan link — the original message is still there, unchanged, readable by anyone.

## What to say

> The database now says 5000. The ledger still says 50. We did not detect this by trusting our own
> system — we detected it by reading the message back from a public network we do not control.

## Proof that the demo works

The same scenario runs as a test, so it cannot fail on stage:

```bash
HEDERA_OPERATOR_ID=... HEDERA_OPERATOR_PRIVATE_KEY=... \
  ./mvnw test -Dtest=AuditTamperDetectionLiveIT
```

It records an event, verifies it, alters the row, and asserts that verification fails and that the
ledger still holds the original amount.

## Restoring a clean state

The tampered row stays wrong forever — that is the correct behaviour, the ledger cannot be edited
to match. Just record a fresh event for the next run, or drop the database and start over.
