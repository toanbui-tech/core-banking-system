ALTER TABLE ledger_entries
    ADD COLUMN currency VARCHAR(3);

UPDATE ledger_entries le
SET currency = a.currency
FROM accounts a
WHERE le.account_id = a.id;

ALTER TABLE ledger_entries
    ALTER COLUMN currency SET NOT NULL;
