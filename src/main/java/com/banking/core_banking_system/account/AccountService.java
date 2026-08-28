package com.banking.core_banking_system.account;

import com.banking.core_banking_system.ledger.LedgerEntryRepository;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.UUID;

@Service
public class AccountService {

  private final AccountRepository accountRepository;
  private final LedgerEntryRepository ledgerEntryRepository;

  public AccountService(AccountRepository accountRepository,
    LedgerEntryRepository ledgerEntryRepository) {
    this.accountRepository = accountRepository;
    this.ledgerEntryRepository = ledgerEntryRepository;
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
}