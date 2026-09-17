CREATE TABLE compliance_records (
    id              RAW(16) PRIMARY KEY,
    transaction_id  RAW(16) NOT NULL,
    account_id      RAW(16) NOT NULL,
    entry_type      VARCHAR2(6) NOT NULL CHECK (entry_type IN ('DEBIT', 'CREDIT')),
    amount          NUMBER(19, 2) NOT NULL,
    currency        VARCHAR2(3) NOT NULL,
    created_by      VARCHAR2(100) NOT NULL,
    recorded_at     TIMESTAMP DEFAULT CURRENT_TIMESTAMP NOT NULL
);

CREATE INDEX idx_compliance_records_transaction_id ON compliance_records(transaction_id);
CREATE INDEX idx_compliance_records_account_id ON compliance_records(account_id);
