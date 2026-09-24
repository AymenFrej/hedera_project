-- A held approval must remember what asset it was for. Envelopes are a budget in HBAR, so
-- approving a token payment must not debit one, and must not be refused for lacking tinybars:
-- 2 BEAN is not 200 tinybars. Existing rows predate token payments and are all HBAR.
ALTER TABLE approvals ADD COLUMN asset VARCHAR(32) NOT NULL DEFAULT 'HBAR';
