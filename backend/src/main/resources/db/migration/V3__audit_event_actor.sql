-- Who the action is attributed to. Resolved by the platform from the session, never taken from
-- client input: an audit trail the audited party can shape proves nothing.
-- The platform still signs and pays for the HCS message, so the trail does not depend on the
-- actor's willingness or HBAR balance.

ALTER TABLE audit_events ADD COLUMN actor_type VARCHAR(16);
ALTER TABLE audit_events ADD COLUMN actor_id VARCHAR(128);
ALTER TABLE audit_events ADD COLUMN actor_hedera_account_id VARCHAR(64);

CREATE INDEX idx_audit_events_actor ON audit_events (actor_type, actor_id);
