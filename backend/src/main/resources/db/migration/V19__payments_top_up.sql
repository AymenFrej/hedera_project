-- Real wallets start with 0 HBAR and cannot pay even a network fee. A TOP_UP is a payment from the
-- platform treasury to the requester's own wallet: the destination comes from their account, never
-- from the request, and it is sent only after an administrator other than the requester approves it.
-- Every existing row is an ordinary payment.
ALTER TABLE payments ADD COLUMN kind VARCHAR(16) NOT NULL DEFAULT 'PAYMENT';
