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

    ledgerService.recordTransaction(List.of(credit, debit));

    BigDecimal balance = accountService.getBalance(account.getId());
    assertEquals(0, balance.compareTo(new BigDecimal("100.00")));
  }
}