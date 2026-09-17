ALTER TABLE ledger_entries
    ADD COLUMN created_by VARCHAR(100) NOT NULL DEFAULT 'system',
    ADD COLUMN reversal_of_entry_id UUID NULL REFERENCES ledger_entries(id);

ALTER TABLE ledger_entries ALTER COLUMN created_by DROP DEFAULT;

CREATE INDEX idx_ledger_entries_reversal_of_entry_id ON ledger_entries(reversal_of_entry_id);
