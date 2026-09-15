package com.banking.core_banking_system.ledger;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

@Service
public class LedgerService {

  private final LedgerEntryRepository ledgerEntryRepository;

  public LedgerService(LedgerEntryRepository ledgerEntryRepository) {
    this.ledgerEntryRepository = ledgerEntryRepository;
  }

  @Transactional
  public void recordTransaction(List<LedgerEntry> entries, String createdBy) {
    validateBalanced(entries);

    UUID transactionId = UUID.randomUUID();
    for (LedgerEntry entry : entries) {
      entry.setTransactionId(transactionId);
      entry.setCreatedBy(createdBy);
    }

    ledgerEntryRepository.saveAll(entries);
  }

  @Transactional
  public void reverseTransaction(UUID transactionId, String reversedBy) {
    List<LedgerEntry> originalEntries = ledgerEntryRepository.findByTransactionId(transactionId);

    if (originalEntries.isEmpty()) {
      throw new IllegalArgumentException("Transaction không tồn tại: " + transactionId);
    }

    UUID reversalTransactionId = UUID.randomUUID();
    List<LedgerEntry> reversalEntries = originalEntries.stream()
      .map(original -> {
        LedgerEntry reversal = new LedgerEntry();
        reversal.setAccountId(original.getAccountId());
        reversal.setTransactionId(reversalTransactionId);
        reversal.setEntryType(original.getEntryType() == EntryType.DEBIT ? EntryType.CREDIT : EntryType.DEBIT);
        reversal.setAmount(original.getAmount());
        reversal.setCreatedBy(reversedBy);
        reversal.setReversalOfEntryId(original.getId());
        return reversal;
      })
      .toList();

    validateBalanced(reversalEntries);
    ledgerEntryRepository.saveAll(reversalEntries);
  }

  private void validateBalanced(List<LedgerEntry> entries) {
    BigDecimal totalDebit = entries.stream()
      .filter(e -> e.getEntryType() == EntryType.DEBIT)
      .map(LedgerEntry::getAmount)
      .reduce(BigDecimal.ZERO, BigDecimal::add);

    BigDecimal totalCredit = entries.stream()
      .filter(e -> e.getEntryType() == EntryType.CREDIT)
      .map(LedgerEntry::getAmount)
      .reduce(BigDecimal.ZERO, BigDecimal::add);

    if (totalDebit.compareTo(totalCredit) != 0) {
      throw new IllegalStateException(
        "Giao dịch không cân bằng: Nợ=" + totalDebit + ", Có=" + totalCredit
      );
    }
  }
}