-- Contacts: names a person can pay by ("Zied"), each tied to one Hedera account. name_key is the
-- lowercased name, so "Zied" and "zied" are the same contact.
CREATE TABLE payment_contacts (
    id VARCHAR(64) PRIMARY KEY,
    name VARCHAR(64) NOT NULL,
    name_key VARCHAR(64) NOT NULL,
    account_id VARCHAR(64) NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL
);
CREATE UNIQUE INDEX ux_payment_contacts_name_key ON payment_contacts (name_key);

-- The requester's own condition on a payment: "keep at least this much of the asset". Checked
-- against the real balance before the policy is asked; not a platform rule.
ALTER TABLE payments ADD COLUMN keep_at_least DECIMAL(38, 8);
