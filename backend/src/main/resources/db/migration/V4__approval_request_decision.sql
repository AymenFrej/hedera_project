-- A HOLD verdict becomes a question put to a human, so the row has to carry enough of the
-- decision to answer it later without re-running the engine: which rule held it, why, and what
-- spend it was. The answer is recorded on the same row (who, when) because "approved" with no
-- name attached is not an approval.
ALTER TABLE approvals ADD COLUMN rule_id VARCHAR(64);
ALTER TABLE approvals ADD COLUMN reason VARCHAR(512);
ALTER TABLE approvals ADD COLUMN envelope VARCHAR(32);
ALTER TABLE approvals ADD COLUMN amount BIGINT;
ALTER TABLE approvals ADD COLUMN counterparty VARCHAR(128);
ALTER TABLE approvals ADD COLUMN decided_at TIMESTAMP WITH TIME ZONE;
ALTER TABLE approvals ADD COLUMN decided_by VARCHAR(128);
