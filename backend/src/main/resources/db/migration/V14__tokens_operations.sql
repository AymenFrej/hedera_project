-- Tokens become real: every token creation and mint the platform asks Hedera for, followed from
-- the request to the ledger. The placeholder "tokens" table from V1 is left untouched.
-- Amounts are in the token's smallest unit (whole tokens x 10^decimals), as Hedera counts them.

CREATE TABLE token_operations (
    id VARCHAR(64) PRIMARY KEY,
    -- CREATE or MINT
    kind VARCHAR(16) NOT NULL,
    -- SUBMITTING, CONFIRMED, FAILED, UNKNOWN (no receipt yet), SIMULATED (no Hedera credentials)
    status VARCHAR(16) NOT NULL,
    token_id VARCHAR(64),
    name VARCHAR(100),
    symbol VARCHAR(100),
    decimals INTEGER,
    -- CREATE: the initial supply; MINT: the amount minted
    amount_units BIGINT,
    -- null: no cap (INFINITE supply type)
    max_supply_units BIGINT,
    mintable BOOLEAN,
    memo VARCHAR(100),
    treasury VARCHAR(64),
    transaction_id VARCHAR(128),
    network_status VARCHAR(64),
    total_supply_after BIGINT,
    failure_reason VARCHAR(512),
    -- What the network really charged, read back from the Mirror Node once verified
    fee_tinybars BIGINT,
    consensus_timestamp VARCHAR(64),
    -- A client-chosen key per attempt, scoped to the requester: a double click creates one token
    idempotency_key VARCHAR(200),
    -- Who asked, resolved server-side (ActorResolver), never from the request body
    requested_by_type VARCHAR(16),
    requested_by_id VARCHAR(128),
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL,
    version BIGINT NOT NULL DEFAULT 0
);

CREATE UNIQUE INDEX ux_token_operations_idempotency_key ON token_operations (idempotency_key);
CREATE INDEX idx_token_operations_created_at ON token_operations (created_at);
CREATE INDEX idx_token_operations_token_id ON token_operations (token_id);
