package com.banking.core_banking_system.ledger;

import com.banking.core_banking_system.account.Account;
import com.banking.core_banking_system.account.AccountRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

@SpringBootTest
class LedgerServiceTest {

  @Autowired
  private LedgerService ledgerService;

  @Autowired
  private AccountRepository accountRepository;

  @Autowired
  private LedgerEntryRepository ledgerEntryRepository;

  private UUID createTestAccount(String prefix) {
    Account account = new Account();
    account.setAccountNumber(prefix + UUID.randomUUID().toString().substring(0, 8));
    account.setAccountType("CASH");
    account.setCurrency("VND");
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
    debit.setAmount(new BigDecimal("100.00"));

    LedgerEntry credit = new LedgerEntry();
    credit.setAccountId(accountB);
    credit.setEntryType(EntryType.CREDIT);
    credit.setAmount(new BigDecimal("100.00"));

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
    debit.setAmount(new BigDecimal("100.00"));

    LedgerEntry credit = new LedgerEntry();
    credit.setAccountId(accountB);
    credit.setEntryType(EntryType.CREDIT);
    credit.setAmount(new BigDecimal("50.00"));

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
    debit.setAmount(new BigDecimal("100.00"));

    LedgerEntry credit = new LedgerEntry();
    credit.setAccountId(accountB);
    credit.setEntryType(EntryType.CREDIT);
    credit.setAmount(new BigDecimal("100.00"));

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
}