-- The demo runway lives in a row, not in a constant. A budget that only exists in memory resets
-- every restart and, worse, never goes down: the console showed 500 rent after spending 400 of it.
CREATE TABLE envelope_balances (
    envelope VARCHAR(32) PRIMARY KEY,
    balance BIGINT NOT NULL
);

INSERT INTO envelope_balances (envelope, balance) VALUES ('RENT', 500);
INSERT INTO envelope_balances (envelope, balance) VALUES ('ESSENTIALS', 300);
INSERT INTO envelope_balances (envelope, balance) VALUES ('EMERGENCY', 200);
