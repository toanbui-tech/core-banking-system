ALTER TABLE ledger_entries ADD (
    created_by VARCHAR2(100) DEFAULT 'system' NOT NULL,
    reversal_of_entry_id RAW(16) NULL REFERENCES ledger_entries(id)
);

ALTER TABLE ledger_entries MODIFY (created_by DEFAULT NULL);

CREATE INDEX idx_ledger_entries_reversal_of_entry_id ON ledger_entries(reversal_of_entry_id);
