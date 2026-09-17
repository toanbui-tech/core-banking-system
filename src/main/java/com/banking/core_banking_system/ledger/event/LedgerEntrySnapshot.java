package com.banking.core_banking_system.ledger.event;

import com.banking.core_banking_system.ledger.EntryType;

import java.math.BigDecimal;
import java.util.UUID;

public record LedgerEntrySnapshot(
  UUID accountId,
  EntryType entryType,
  BigDecimal amount,
  String currency
) {
}
