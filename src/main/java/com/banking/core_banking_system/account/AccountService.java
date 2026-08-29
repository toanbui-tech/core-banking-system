package com.banking.core_banking_system.account;

import com.banking.core_banking_system.ledger.EntryType;
import com.banking.core_banking_system.ledger.LedgerEntry;
import com.banking.core_banking_system.ledger.LedgerEntryRepository;
import com.banking.core_banking_system.ledger.LedgerService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

@Service
public class AccountService {

  private final AccountRepository accountRepository;
  private final LedgerEntryRepository ledgerEntryRepository;
  private final LedgerService ledgerService;

  public AccountService(AccountRepository accountRepository,
    LedgerEntryRepository ledgerEntryRepository,
    LedgerService ledgerService) {
    this.accountRepository = accountRepository;
    this.ledgerEntryRepository = ledgerEntryRepository;
    this.ledgerService = ledgerService;
  }

  public Account createAccount(String accountNumber, String accountType, String currency) {
    Account account = new Account();
    account.setAccountNumber(accountNumber);
    account.setAccountType(accountType);
    account.setCurrency(currency);
    account.setStatus("ACTIVE");
    return accountRepository.save(account);
  }

  public BigDecimal getBalance(UUID accountId) {
    BigDecimal totalCredit = ledgerEntryRepository.sumCreditByAccountId(accountId);
    BigDecimal totalDebit = ledgerEntryRepository.sumDebitByAccountId(accountId);
    return totalCredit.subtract(totalDebit);
  }

  @Transactional
  public void withdraw(UUID accountId, UUID counterpartyAccountId, BigDecimal amount) {
    Account account = accountRepository.findByIdForUpdate(accountId)
      .orElseThrow(() -> new IllegalArgumentException("Account not found: " + accountId));

    BigDecimal currentBalance = getBalance(accountId);

    if (currentBalance.compareTo(amount) < 0) {
      throw new IllegalStateException(
        "Insufficient balance: current=" + currentBalance + ", requested=" + amount
      );
    }

    LedgerEntry debit = new LedgerEntry();
    debit.setAccountId(accountId);
    debit.setEntryType(EntryType.DEBIT);
    debit.setAmount(amount);

    LedgerEntry credit = new LedgerEntry();
    credit.setAccountId(counterpartyAccountId);
    credit.setEntryType(EntryType.CREDIT);
    credit.setAmount(amount);

    ledgerService.recordTransaction(List.of(debit, credit));
  }
}