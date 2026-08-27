package com.banking.core_banking_system.ledger;

import org.springframework.data.jpa.repository.JpaRepository;
import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

public interface LedgerEntryRepository extends JpaRepository<LedgerEntry, UUID> {
  List<LedgerEntry> findByAccountId(UUID accountId);
  List<LedgerEntry> findByTransactionId(UUID transactionId);
}