ALTER TABLE ledger_entries ADD (currency VARCHAR2(3));

UPDATE ledger_entries le
SET currency = (SELECT a.currency FROM accounts a WHERE a.id = le.account_id)
WHERE EXISTS (SELECT 1 FROM accounts a WHERE a.id = le.account_id);

ALTER TABLE ledger_entries MODIFY (currency NOT NULL);
