-- What the envelope held when the policy decided, in the payment's smallest unit. The ledger keeps
-- moving afterwards, so an explanation of a past decision needs the balance it was made on.
ALTER TABLE payments ADD COLUMN policy_envelope_balance BIGINT;
