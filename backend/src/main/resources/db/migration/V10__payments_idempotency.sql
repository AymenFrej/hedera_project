-- A client-chosen key per payment attempt. Sending the same request twice (double click, retry
-- after a network error) returns the first payment instead of paying twice.
-- NULLs are allowed any number of times: payments created without a key are not deduplicated.

ALTER TABLE payments ADD COLUMN idempotency_key VARCHAR(64);

CREATE UNIQUE INDEX ux_payments_idempotency_key ON payments (idempotency_key);
