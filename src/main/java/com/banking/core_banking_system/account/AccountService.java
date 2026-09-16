package com.banking.core_banking_system.account;

import com.banking.core_banking_system.ledger.EntryType;
import com.banking.core_banking_system.ledger.LedgerEntry;
import com.banking.core_banking_system.ledger.LedgerEntryRepository;
import com.banking.core_banking_system.ledger.LedgerService;
import com.banking.core_banking_system.shared.money.CurrencyMismatchException;
import com.banking.core_banking_system.shared.money.Money;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.Currency;
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

  public Account createAccount(String accountNumber, String accountType, String currencyCode) {
    Account account = new Account();
    account.setAccountNumber(accountNumber);
    account.setAccountType(accountType);
    account.setCurrency(Currency.getInstance(currencyCode));
    account.setStatus("ACTIVE");
    return accountRepository.save(account);
  }

  public Money getBalance(UUID accountId) {
    Account account = accountRepository.findById(accountId)
      .orElseThrow(() -> new IllegalArgumentException("Account not found: " + accountId));

    BigDecimal totalCredit = ledgerEntryRepository.sumCreditByAccountId(accountId);
    BigDecimal totalDebit = ledgerEntryRepository.sumDebitByAccountId(accountId);

    return Money.of(totalCredit, account.getCurrency())
      .subtract(Money.of(totalDebit, account.getCurrency()));
  }

  @Transactional
  public void withdraw(UUID accountId, UUID counterpartyAccountId, Money amount, String createdBy) {
    Account account = accountRepository.findByIdForUpdate(accountId)
      .orElseThrow(() -> new IllegalArgumentException("Account not found: " + accountId));

    if (!account.getCurrency().equals(amount.getCurrency())) {
      throw new CurrencyMismatchException(account.getCurrency(), amount.getCurrency());
    }

    Money currentBalance = getBalance(accountId);

    if (currentBalance.isLessThan(amount)) {
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

    ledgerService.recordTransaction(List.of(debit, credit), createdBy);
  }
}