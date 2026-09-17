CREATE TABLE accounts (
    id              RAW(16) PRIMARY KEY,
    account_number  VARCHAR2(20) NOT NULL UNIQUE,
    account_type    VARCHAR2(30) NOT NULL,
    currency        VARCHAR2(3) DEFAULT 'VND' NOT NULL,
    status          VARCHAR2(20) DEFAULT 'ACTIVE' NOT NULL,
    created_at      TIMESTAMP DEFAULT CURRENT_TIMESTAMP NOT NULL
);

CREATE TABLE ledger_entries (
    id              RAW(16) PRIMARY KEY,
    account_id      RAW(16) NOT NULL REFERENCES accounts(id),
    transaction_id  RAW(16) NOT NULL,
    entry_type      VARCHAR2(6) NOT NULL CHECK (entry_type IN ('DEBIT', 'CREDIT')),
    amount          NUMBER(19, 2) NOT NULL CHECK (amount > 0),
    created_at      TIMESTAMP DEFAULT CURRENT_TIMESTAMP NOT NULL
);

CREATE INDEX idx_ledger_entries_account_id ON ledger_entries(account_id);
CREATE INDEX idx_ledger_entries_transaction_id ON ledger_entries(transaction_id);
