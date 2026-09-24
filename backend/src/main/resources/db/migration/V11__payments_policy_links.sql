-- Links a payment to the Policies module's records: the audit event its decision was recorded as,
-- and the approval opened when the decision was HOLD. The decision and approval live there.
ALTER TABLE payments ADD COLUMN policy_audit_event_id VARCHAR(64);
ALTER TABLE payments ADD COLUMN approval_id VARCHAR(64);
