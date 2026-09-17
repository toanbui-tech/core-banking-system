package com.banking.core_banking_system.ledger;

import com.banking.core_banking_system.account.Account;
import com.banking.core_banking_system.account.AccountRepository;
import com.banking.core_banking_system.outbox.OutboxEvent;
import com.banking.core_banking_system.outbox.OutboxEventRepository;
import com.banking.core_banking_system.shared.money.Money;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.math.BigDecimal;
import java.util.Currency;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest(properties = "outbox.publisher.enabled=false")
class LedgerServiceTest {

  @Autowired
  private LedgerService ledgerService;

  @Autowired
  private AccountRepository accountRepository;

  @Autowired
  private LedgerEntryRepository ledgerEntryRepository;

  @Autowired
  private OutboxEventRepository outboxEventRepository;

  private UUID createTestAccount(String prefix) {
    Account account = new Account();
    account.setAccountNumber(prefix + UUID.randomUUID().toString().substring(0, 8));
    account.setAccountType("CASH");
    account.setCurrency(Currency.getInstance("VND"));
    account.setStatus("ACTIVE");
    return accountRepository.save(account).getId();
  }

  @Test
  void recordTransaction_shouldSucceed_whenDebitEqualsCredit() {
    UUID accountA = createTestAccount("TEST-A-");
    UUID accountB = createTestAccount("TEST-B-");

    LedgerEntry debit = new LedgerEntry();
    debit.setAccountId(accountA);
    debit.setEntryType(EntryType.DEBIT);
    debit.setAmount(Money.of(new BigDecimal("100.00"), "VND"));

    LedgerEntry credit = new LedgerEntry();
    credit.setAccountId(accountB);
    credit.setEntryType(EntryType.CREDIT);
    credit.setAmount(Money.of(new BigDecimal("100.00"), "VND"));

    assertDoesNotThrow(() ->
      ledgerService.recordTransaction(List.of(debit, credit), "test-user")
    );
  }

  @Test
  void recordTransaction_shouldThrow_whenDebitNotEqualsCredit() {
    UUID accountA = createTestAccount("TEST-C-");
    UUID accountB = createTestAccount("TEST-D-");

    LedgerEntry debit = new LedgerEntry();
    debit.setAccountId(accountA);
    debit.setEntryType(EntryType.DEBIT);
    debit.setAmount(Money.of(new BigDecimal("100.00"), "VND"));

    LedgerEntry credit = new LedgerEntry();
    credit.setAccountId(accountB);
    credit.setEntryType(EntryType.CREDIT);
    credit.setAmount(Money.of(new BigDecimal("50.00"), "VND"));

    assertThrows(IllegalStateException.class, () ->
      ledgerService.recordTransaction(List.of(debit, credit), "test-user")
    );
  }

  @Test
  void reverseTransaction_shouldCreateOffsettingEntries_thatRestoreOriginalBalance() {
    UUID accountA = createTestAccount("TEST-E-");
    UUID accountB = createTestAccount("TEST-F-");

    LedgerEntry debit = new LedgerEntry();
    debit.setAccountId(accountA);
    debit.setEntryType(EntryType.DEBIT);
    debit.setAmount(Money.of(new BigDecimal("100.00"), "VND"));

    LedgerEntry credit = new LedgerEntry();
    credit.setAccountId(accountB);
    credit.setEntryType(EntryType.CREDIT);
    credit.setAmount(Money.of(new BigDecimal("100.00"), "VND"));

    ledgerService.recordTransaction(List.of(debit, credit), "test-user");
    UUID transactionId = debit.getTransactionId();

    ledgerService.reverseTransaction(transactionId, "reversal-user");

    List<LedgerEntry> accountAEntries = ledgerEntryRepository.findByAccountId(accountA);
    List<LedgerEntry> accountBEntries = ledgerEntryRepository.findByAccountId(accountB);

    assertEquals(2, accountAEntries.size());
    assertEquals(2, accountBEntries.size());

    LedgerEntry reversalOnA = accountAEntries.stream()
      .filter(e -> e.getReversalOfEntryId() != null)
      .findFirst()
      .orElseThrow();
    assertEquals(EntryType.CREDIT, reversalOnA.getEntryType());
    assertEquals(debit.getId(), reversalOnA.getReversalOfEntryId());
    assertEquals("reversal-user", reversalOnA.getCreatedBy());
  }

  @Test
  void reverseTransaction_shouldThrow_whenTransactionDoesNotExist() {
    assertThrows(IllegalArgumentException.class, () ->
      ledgerService.reverseTransaction(UUID.randomUUID(), "reversal-user")
    );
  }

  @Test
  void recordTransaction_shouldCreateUnpublishedOutboxEvent_withCorrectAggregateAndType() {
    UUID accountA = createTestAccount("TEST-G-");
    UUID accountB = createTestAccount("TEST-H-");

    LedgerEntry debit = new LedgerEntry();
    debit.setAccountId(accountA);
    debit.setEntryType(EntryType.DEBIT);
    debit.setAmount(Money.of(new BigDecimal("100.00"), "VND"));

    LedgerEntry credit = new LedgerEntry();
    credit.setAccountId(accountB);
    credit.setEntryType(EntryType.CREDIT);
    credit.setAmount(Money.of(new BigDecimal("100.00"), "VND"));

    ledgerService.recordTransaction(List.of(debit, credit), "test-user");
    UUID transactionId = debit.getTransactionId();

    List<OutboxEvent> events = outboxEventRepository.findAll().stream()
      .filter(e -> e.getAggregateId().equals(transactionId))
      .toList();

    assertEquals(1, events.size());
    OutboxEvent event = events.get(0);
    assertEquals("TransactionPostedEvent", event.getEventType());
    assertNull(event.getPublishedAt());
    assertTrue(event.getPayload().contains(transactionId.toString()));
  }

  @Test
  void reverseTransaction_shouldCreateUnpublishedOutboxEvent_forReversalTransaction() {
    UUID accountA = createTestAccount("TEST-I-");
    UUID accountB = createTestAccount("TEST-J-");

    LedgerEntry debit = new LedgerEntry();
    debit.setAccountId(accountA);
    debit.setEntryType(EntryType.DEBIT);
    debit.setAmount(Money.of(new BigDecimal("100.00"), "VND"));

    LedgerEntry credit = new LedgerEntry();
    credit.setAccountId(accountB);
    credit.setEntryType(EntryType.CREDIT);
    credit.setAmount(Money.of(new BigDecimal("100.00"), "VND"));

    ledgerService.recordTransaction(List.of(debit, credit), "test-user");
    UUID originalTransactionId = debit.getTransactionId();

    ledgerService.reverseTransaction(originalTransactionId, "reversal-user");

    List<OutboxEvent> reversalEvents = outboxEventRepository.findAll().stream()
      .filter(e -> e.getEventType().equals("TransactionReversedEvent"))
      .filter(e -> e.getPayload().contains(originalTransactionId.toString()))
      .toList();

    assertEquals(1, reversalEvents.size());
    assertNull(reversalEvents.get(0).getPublishedAt());
  }
}