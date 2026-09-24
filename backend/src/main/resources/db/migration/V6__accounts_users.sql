CREATE TABLE users (
    id VARCHAR(64) PRIMARY KEY,
    email VARCHAR(320) NOT NULL UNIQUE,
    display_name VARCHAR(128) NOT NULL,
    password_hash VARCHAR(128) NOT NULL,
    role VARCHAR(32) NOT NULL,
    account_id VARCHAR(64)
);
ALTER TABLE accounts ADD COLUMN user_id VARCHAR(64);
ALTER TABLE accounts ADD COLUMN email VARCHAR(320);
