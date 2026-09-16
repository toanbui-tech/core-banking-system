package com.banking.core_banking_system.ledger;

import com.banking.core_banking_system.shared.money.CurrencyMismatchException;
import com.banking.core_banking_system.shared.money.Money;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class TransactionTest {

  private LedgerEntry entry(UUID accountId, EntryType type, Money amount) {
    LedgerEntry entry = new LedgerEntry();
    entry.setAccountId(accountId);
    entry.setEntryType(type);
    entry.setAmount(amount);
    return entry;
  }

  @Test
  void record_shouldSucceed_whenDebitEqualsCredit() {
    LedgerEntry debit = entry(UUID.randomUUID(), EntryType.DEBIT, Money.of(new BigDecimal("100.00"), "VND"));
    LedgerEntry credit = entry(UUID.randomUUID(), EntryType.CREDIT, Money.of(new BigDecimal("100.00"), "VND"));

    assertDoesNotThrow(() -> Transaction.record(List.of(debit, credit), "test-user"));
  }

  @Test
  void record_shouldThrow_whenDebitNotEqualsCredit() {
    LedgerEntry debit = entry(UUID.randomUUID(), EntryType.DEBIT, Money.of(new BigDecimal("100.00"), "VND"));
    LedgerEntry credit = entry(UUID.randomUUID(), EntryType.CREDIT, Money.of(new BigDecimal("50.00"), "VND"));

    assertThrows(IllegalStateException.class, () -> Transaction.record(List.of(debit, credit), "test-user"));
  }

  @Test
  void record_shouldThrow_whenEntriesMixCurrencies() {
    LedgerEntry debit = entry(UUID.randomUUID(), EntryType.DEBIT, Money.of(new BigDecimal("100.00"), "VND"));
    LedgerEntry credit = entry(UUID.randomUUID(), EntryType.CREDIT, Money.of(new BigDecimal("100.00"), "USD"));

    assertThrows(CurrencyMismatchException.class, () -> Transaction.record(List.of(debit, credit), "test-user"));
  }

  @Test
  void record_shouldThrow_whenEntriesEmpty() {
    assertThrows(IllegalArgumentException.class, () -> Transaction.record(List.of(), "test-user"));
  }

  @Test
  void record_shouldAssignSameTransactionToAllEntries() {
    LedgerEntry debit = entry(UUID.randomUUID(), EntryType.DEBIT, Money.of(new BigDecimal("100.00"), "VND"));
    LedgerEntry credit = entry(UUID.randomUUID(), EntryType.CREDIT, Money.of(new BigDecimal("100.00"), "VND"));

    Transaction transaction = Transaction.record(List.of(debit, credit), "test-user");

    assertEquals(transaction.getId(), debit.getTransactionId());
    assertEquals(transaction.getId(), credit.getTransactionId());
  }

  @Test
  void record_shouldPropagateCreatedByToAllEntries() {
    LedgerEntry debit = entry(UUID.randomUUID(), EntryType.DEBIT, Money.of(new BigDecimal("100.00"), "VND"));
    LedgerEntry credit = entry(UUID.randomUUID(), EntryType.CREDIT, Money.of(new BigDecimal("100.00"), "VND"));

    Transaction.record(List.of(debit, credit), "test-user");

    assertEquals("test-user", debit.getCreatedBy());
    assertEquals("test-user", credit.getCreatedBy());
  }

  @Test
  void reverse_shouldCreateOffsettingEntries_linkedToOriginalTransaction() {
    UUID accountA = UUID.randomUUID();
    UUID accountB = UUID.randomUUID();

    LedgerEntry debit = entry(accountA, EntryType.DEBIT, Money.of(new BigDecimal("100.00"), "VND"));
    LedgerEntry credit = entry(accountB, EntryType.CREDIT, Money.of(new BigDecimal("100.00"), "VND"));
    Transaction original = Transaction.record(List.of(debit, credit), "test-user");

    Transaction reversal = original.reverse("reversal-user");

    assertEquals(original.getId(), reversal.getReversalOfTransactionId());
    assertEquals(2, reversal.getEntries().size());

    LedgerEntry reversalOnA = reversal.getEntries().stream()
      .filter(e -> e.getAccountId().equals(accountA))
      .findFirst()
      .orElseThrow();
    assertEquals(EntryType.CREDIT, reversalOnA.getEntryType());
    assertEquals(debit.getId(), reversalOnA.getReversalOfEntryId());
    assertEquals("reversal-user", reversalOnA.getCreatedBy());
  }
}
