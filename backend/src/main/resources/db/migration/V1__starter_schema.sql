CREATE TABLE accounts (
    id VARCHAR(64) PRIMARY KEY,
    hedera_account_id VARCHAR(64),
    balance DECIMAL(38, 8) NOT NULL DEFAULT 0,
    status VARCHAR(32) NOT NULL
);

CREATE TABLE payments (
    id VARCHAR(64) PRIMARY KEY,
    amount DECIMAL(38, 8) NOT NULL,
    currency VARCHAR(32) NOT NULL,
    destination VARCHAR(128) NOT NULL,
    status VARCHAR(32) NOT NULL
);

CREATE TABLE tokens (
    id VARCHAR(64) PRIMARY KEY,
    symbol VARCHAR(32) NOT NULL,
    name VARCHAR(128) NOT NULL,
    status VARCHAR(32) NOT NULL
);

CREATE TABLE audit_events (
    id VARCHAR(64) PRIMARY KEY,
    agent VARCHAR(64) NOT NULL,
    action VARCHAR(64) NOT NULL,
    status VARCHAR(32) NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL
);

CREATE TABLE policies (
    id VARCHAR(64) PRIMARY KEY,
    name VARCHAR(128) NOT NULL,
    description VARCHAR(512),
    status VARCHAR(32) NOT NULL
);

CREATE TABLE approvals (
    id VARCHAR(64) PRIMARY KEY,
    task_id VARCHAR(64) NOT NULL,
    status VARCHAR(32) NOT NULL,
    requested_at TIMESTAMP WITH TIME ZONE NOT NULL
);
