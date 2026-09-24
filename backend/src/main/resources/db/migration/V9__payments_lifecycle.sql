-- Payments become real: each row follows a payment from request to ledger.
-- amount keeps the value as the user typed it; amount_units is what is actually sent
-- (tinybars for HBAR, smallest token unit for HTS), so no rounding happens at execution time.

ALTER TABLE payments ADD COLUMN token_id VARCHAR(64);
ALTER TABLE payments ADD COLUMN amount_units BIGINT;
ALTER TABLE payments ADD COLUMN envelope VARCHAR(32);
ALTER TABLE payments ADD COLUMN memo VARCHAR(100);
ALTER TABLE payments ADD COLUMN source_account VARCHAR(64);
ALTER TABLE payments ADD COLUMN transaction_id VARCHAR(128);
ALTER TABLE payments ADD COLUMN policy_verdict VARCHAR(16);
ALTER TABLE payments ADD COLUMN policy_rule_id VARCHAR(64);
ALTER TABLE payments ADD COLUMN policy_reason VARCHAR(512);
ALTER TABLE payments ADD COLUMN failure_reason VARCHAR(512);
-- Who asked for the payment, resolved server-side (ActorResolver), never from the request body.
ALTER TABLE payments ADD COLUMN requested_by_type VARCHAR(16);
ALTER TABLE payments ADD COLUMN requested_by_id VARCHAR(128);
ALTER TABLE payments ADD COLUMN created_at TIMESTAMP WITH TIME ZONE;
ALTER TABLE payments ADD COLUMN updated_at TIMESTAMP WITH TIME ZONE;
-- Optimistic lock: two concurrent approvals of the same payment must not send it twice.
ALTER TABLE payments ADD COLUMN version BIGINT NOT NULL DEFAULT 0;

CREATE INDEX idx_payments_status ON payments (status);
CREATE INDEX idx_payments_created_at ON payments (created_at);
