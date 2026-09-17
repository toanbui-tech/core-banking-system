package com.banking.core_banking_system.ledger.event;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

public record TransactionPostedEvent(
  UUID eventId,
  UUID transactionId,
  String createdBy,
  LocalDateTime occurredAt,
  List<LedgerEntrySnapshot> entries
) {
}
