-- Demo: alter an audit record the way someone with database access would.
--
-- Run it, then click "Verify" on that event in the UI. Verification turns red and shows the two
-- hashes side by side: the one in our database, and the one on the ledger.
--
--   psql -h localhost -U postgres -d hedera_agents -f demo/tamper.sql
--
-- Nothing here touches Hedera. That is the whole point: the database can be rewritten, the ledger
-- cannot, and that is how the change becomes visible.

\set ON_ERROR_STOP on

-- Most recent anchored event that mentions an amount.
WITH target AS (
    SELECT id, payload
    FROM audit_events
    WHERE anchor_status = 'ANCHORED'
      AND payload LIKE '%"amount"%'
    ORDER BY created_at DESC
    LIMIT 1
)
UPDATE audit_events a
SET payload = replace(t.payload, '"amount":"50"', '"amount":"5000"'),
    -- Recompute the stored hash so the row stays internally consistent: nothing in our own
    -- database reveals the change. Only the ledger disagrees.
    payload_hash = encode(
        digest(replace(t.payload, '"amount":"50"', '"amount":"5000"'), 'sha256'),
        'hex')
FROM target t
WHERE a.id = t.id;

-- What was changed.
SELECT id,
       agent,
       action,
       substring(payload from '"amount":"[0-9]+"') AS amount_now,
       payload_hash AS hash_in_database
FROM audit_events
WHERE anchor_status = 'ANCHORED'
ORDER BY created_at DESC
LIMIT 1;
