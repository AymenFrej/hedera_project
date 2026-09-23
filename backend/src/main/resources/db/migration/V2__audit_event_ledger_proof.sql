-- Audit events become verifiable: alongside the business fields we keep the proof
-- returned by Hedera (topic, transaction id, consensus timestamp, sequence number)
-- and a hash of the payload so the on-ledger message can be compared with what we stored.

ALTER TABLE audit_events ADD COLUMN payload TEXT;
ALTER TABLE audit_events ADD COLUMN payload_hash VARCHAR(64);
ALTER TABLE audit_events ADD COLUMN topic_id VARCHAR(64);
ALTER TABLE audit_events ADD COLUMN transaction_id VARCHAR(128);
ALTER TABLE audit_events ADD COLUMN consensus_timestamp VARCHAR(64);
ALTER TABLE audit_events ADD COLUMN sequence_number BIGINT;
ALTER TABLE audit_events ADD COLUMN anchor_status VARCHAR(32) NOT NULL DEFAULT 'PENDING';

CREATE INDEX idx_audit_events_topic ON audit_events (topic_id);
CREATE INDEX idx_audit_events_anchor_status ON audit_events (anchor_status);
