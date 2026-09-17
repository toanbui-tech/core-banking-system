package com.banking.core_banking_system.ledger;

import com.banking.core_banking_system.ledger.event.LedgerEntrySnapshot;
import com.banking.core_banking_system.ledger.event.TransactionPostedEvent;
import com.banking.core_banking_system.ledger.event.TransactionReversedEvent;
import com.banking.core_banking_system.shared.money.CurrencyMismatchException;
import com.banking.core_banking_system.shared.money.Money;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

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

  @Test
  void record_shouldRaiseTransactionPostedEvent_withCorrectData() {
    UUID accountA = UUID.randomUUID();
    UUID accountB = UUID.randomUUID();
    LedgerEntry debit = entry(accountA, EntryType.DEBIT, Money.of(new BigDecimal("100.00"), "VND"));
    LedgerEntry credit = entry(accountB, EntryType.CREDIT, Money.of(new BigDecimal("100.00"), "VND"));

    Transaction transaction = Transaction.record(List.of(debit, credit), "test-user");

    List<Object> events = transaction.pullDomainEvents();
    assertEquals(1, events.size());
    assertInstanceOf(TransactionPostedEvent.class, events.get(0));

    TransactionPostedEvent event = (TransactionPostedEvent) events.get(0);
    assertEquals(transaction.getId(), event.transactionId());
    assertEquals("test-user", event.createdBy());
    assertEquals(2, event.entries().size());

    LedgerEntrySnapshot debitSnapshot = event.entries().stream()
      .filter(e -> e.accountId().equals(accountA))
      .findFirst()
      .orElseThrow();
    assertEquals(EntryType.DEBIT, debitSnapshot.entryType());
    assertEquals(0, new BigDecimal("100.00").compareTo(debitSnapshot.amount()));
    assertEquals("VND", debitSnapshot.currency());
  }

  @Test
  void pullDomainEvents_shouldClearQueue_soEventsAreNotPublishedTwice() {
    LedgerEntry debit = entry(UUID.randomUUID(), EntryType.DEBIT, Money.of(new BigDecimal("100.00"), "VND"));
    LedgerEntry credit = entry(UUID.randomUUID(), EntryType.CREDIT, Money.of(new BigDecimal("100.00"), "VND"));
    Transaction transaction = Transaction.record(List.of(debit, credit), "test-user");

    assertEquals(1, transaction.pullDomainEvents().size());
    assertTrue(transaction.pullDomainEvents().isEmpty());
  }

  @Test
  void reverse_shouldRaiseTransactionReversedEvent_withCorrectData() {
    LedgerEntry debit = entry(UUID.randomUUID(), EntryType.DEBIT, Money.of(new BigDecimal("100.00"), "VND"));
    LedgerEntry credit = entry(UUID.randomUUID(), EntryType.CREDIT, Money.of(new BigDecimal("100.00"), "VND"));
    Transaction original = Transaction.record(List.of(debit, credit), "test-user");
    original.pullDomainEvents();

    Transaction reversal = original.reverse("reversal-user");

    List<Object> events = reversal.pullDomainEvents();
    assertEquals(1, events.size());
    assertInstanceOf(TransactionReversedEvent.class, events.get(0));

    TransactionReversedEvent event = (TransactionReversedEvent) events.get(0);
    assertEquals(reversal.getId(), event.transactionId());
    assertEquals(original.getId(), event.reversalOfTransactionId());
    assertEquals("reversal-user", event.createdBy());
    assertEquals(2, event.entries().size());
  }
}
