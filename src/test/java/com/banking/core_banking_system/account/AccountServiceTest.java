package com.banking.core_banking_system.account;

import com.banking.core_banking_system.ledger.EntryType;
import com.banking.core_banking_system.ledger.LedgerEntry;
import com.banking.core_banking_system.ledger.LedgerService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;

@SpringBootTest
class AccountServiceTest {

  @Autowired
  private AccountService accountService;

  @Autowired
  private LedgerService ledgerService;

  @Test
  void getBalance_shouldReflectCreditMinusDebit() {
    Account account = accountService.createAccount(
      "BAL-" + UUID.randomUUID().toString().substring(0, 8), "CASH", "VND");
    Account counterparty = accountService.createAccount(
      "CTP-" + UUID.randomUUID().toString().substring(0, 8), "CASH", "VND");

    // Khách gửi 100.00 vào account -> account tăng -> ghi Có
    LedgerEntry credit = new LedgerEntry();
    credit.setAccountId(account.getId());
    credit.setEntryType(EntryType.CREDIT);
    credit.setAmount(new BigDecimal("100.00"));

    LedgerEntry debit = new LedgerEntry();
    debit.setAccountId(counterparty.getId());
    debit.setEntryType(EntryType.DEBIT);
    debit.setAmount(new BigDecimal("100.00"));

    ledgerService.recordTransaction(List.of(credit, debit), "test-user");

    BigDecimal balance = accountService.getBalance(account.getId());
    assertEquals(0, balance.compareTo(new BigDecimal("100.00")));
  }

  @Test
  void withdraw_shouldPreventOverdraft_whenConcurrentRequests() throws InterruptedException {
    Account account = accountService.createAccount(
      "CONC-" + UUID.randomUUID().toString().substring(0, 8), "CASH", "VND");
    Account counterparty = accountService.createAccount(
      "CTP-" + UUID.randomUUID().toString().substring(0, 8), "CASH", "VND");

    LedgerEntry initialCredit = new LedgerEntry();
    initialCredit.setAccountId(account.getId());
    initialCredit.setEntryType(EntryType.CREDIT);
    initialCredit.setAmount(new BigDecimal("100.00"));

    LedgerEntry initialDebit = new LedgerEntry();
    initialDebit.setAccountId(counterparty.getId());
    initialDebit.setEntryType(EntryType.DEBIT);
    initialDebit.setAmount(new BigDecimal("100.00"));

    ledgerService.recordTransaction(List.of(initialCredit, initialDebit), "test-user");

    int threadCount = 2;
    ExecutorService executor = Executors.newFixedThreadPool(threadCount);
    CountDownLatch latch = new CountDownLatch(threadCount);
    AtomicInteger successCount = new AtomicInteger(0);

    for (int i = 0; i < threadCount; i++) {
      executor.submit(() -> {
        try {
          accountService.withdraw(account.getId(), counterparty.getId(), new BigDecimal("80.00"), "test-user");
          successCount.incrementAndGet();
        } catch (IllegalStateException e) {
          // Insufficient balance -- expected cho 1 trong 2 thread
        } finally {
          latch.countDown();
        }
      });
    }

    latch.await();
    executor.shutdown();

    assertEquals(1, successCount.get(), "Chỉ đúng 1 trong 2 giao dịch rút tiền đồng thời được thành công");
  }
}