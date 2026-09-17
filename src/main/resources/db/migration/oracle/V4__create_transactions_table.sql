CREATE TABLE transactions (
    id                          RAW(16) PRIMARY KEY,
    created_by                  VARCHAR2(100) NOT NULL,
    created_at                  TIMESTAMP NOT NULL,
    reversal_of_transaction_id  RAW(16) NULL REFERENCES transactions(id)
);

INSERT INTO transactions (id, created_by, created_at)
SELECT transaction_id, MIN(created_by), MIN(created_at)
FROM ledger_entries
GROUP BY transaction_id;

MERGE INTO transactions t
USING (
    SELECT r.transaction_id AS reversal_transaction_id, MIN(o.transaction_id) AS original_transaction_id
    FROM ledger_entries r
    JOIN ledger_entries o ON r.reversal_of_entry_id = o.id
    WHERE r.reversal_of_entry_id IS NOT NULL
    GROUP BY r.transaction_id
) sub
ON (t.id = sub.reversal_transaction_id)
WHEN MATCHED THEN UPDATE SET t.reversal_of_transaction_id = sub.original_transaction_id;

CREATE INDEX idx_transactions_reversal_of_transaction_id ON transactions(reversal_of_transaction_id);

ALTER TABLE ledger_entries
    ADD CONSTRAINT fk_ledger_entries_transaction
    FOREIGN KEY (transaction_id) REFERENCES transactions(id);
