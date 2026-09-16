package com.banking.core_banking_system.ledger;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
public class LedgerService {

  private final TransactionRepository transactionRepository;

  public LedgerService(TransactionRepository transactionRepository) {
    this.transactionRepository = transactionRepository;
  }

  @Transactional
  public void recordTransaction(List<LedgerEntry> entries, String createdBy) {
    Transaction transaction = Transaction.record(entries, createdBy);
    transactionRepository.save(transaction);
  }

  @Transactional
  public void reverseTransaction(UUID transactionId, String reversedBy) {
    Transaction original = transactionRepository.findById(transactionId)
      .orElseThrow(() -> new IllegalArgumentException("Transaction không tồn tại: " + transactionId));

    Transaction reversal = original.reverse(reversedBy);
    transactionRepository.save(reversal);
  }
}
