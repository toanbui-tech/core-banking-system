CREATE TABLE compliance_records (
    id              UUID PRIMARY KEY,
    transaction_id  UUID NOT NULL,
    account_id      UUID NOT NULL,
    entry_type      VARCHAR(6) NOT NULL CHECK (entry_type IN ('DEBIT', 'CREDIT')),
    amount          NUMERIC(19, 2) NOT NULL,
    currency        VARCHAR(3) NOT NULL,
    created_by      VARCHAR(100) NOT NULL,
    recorded_at     TIMESTAMP NOT NULL DEFAULT now()
);

CREATE INDEX idx_compliance_records_transaction_id ON compliance_records(transaction_id);
CREATE INDEX idx_compliance_records_account_id ON compliance_records(account_id);
