package com.banking.core_banking_system.ledger;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

public interface LedgerEntryRepository extends JpaRepository<LedgerEntry, UUID> {
  List<LedgerEntry> findByAccountId(UUID accountId);

  @Query("SELECT COALESCE(SUM(e.amount.amount), 0) FROM LedgerEntry e " +
    "WHERE e.accountId = :accountId AND e.entryType = 'DEBIT'")
  BigDecimal sumDebitByAccountId(@Param("accountId") UUID accountId);

  @Query("SELECT COALESCE(SUM(e.amount.amount), 0) FROM LedgerEntry e " +
    "WHERE e.accountId = :accountId AND e.entryType = 'CREDIT'")
  BigDecimal sumCreditByAccountId(@Param("accountId") UUID accountId);
}