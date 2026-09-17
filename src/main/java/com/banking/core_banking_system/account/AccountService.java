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
  private final AccountBalanceCache balanceCache;

  public AccountService(AccountRepository accountRepository,
    LedgerEntryRepository ledgerEntryRepository,
    LedgerService ledgerService,
    AccountBalanceCache balanceCache) {
    this.accountRepository = accountRepository;
    this.ledgerEntryRepository = ledgerEntryRepository;
    this.ledgerService = ledgerService;
    this.balanceCache = balanceCache;
  }

  public Account createAccount(String accountNumber, String accountType, String currencyCode) {
    Account account = new Account();
    account.setAccountNumber(accountNumber);
    account.setAccountType(accountType);
    account.setCurrency(Currency.getInstance(currencyCode));
    account.setStatus("ACTIVE");
    return accountRepository.save(account);
  }

  /**
   * Cache-aside: đọc Redis trước, nếu miss thì tính từ DB rồi lưu lại vào cache.
   * KHÔNG dùng cho việc kiểm tra invariant trong withdraw() — xem computeBalanceFromDb().
   */
  public Money getBalance(UUID accountId) {
    Account account = accountRepository.findById(accountId)
      .orElseThrow(() -> new IllegalArgumentException("Account not found: " + accountId));

    return balanceCache.get(accountId)
      .map(amount -> Money.of(amount, account.getCurrency()))
      .orElseGet(() -> {
        Money balance = computeBalanceFromDb(accountId, account.getCurrency());
        balanceCache.put(accountId, balance.getAmount());
        return balance;
      });
  }

  /**
   * Tính balance trực tiếp từ DB, bỏ qua cache. Dùng riêng cho withdraw() vì bước kiểm tra
   * đủ số dư nằm trong pessimistic lock — đọc cache ở đây có thể trả về balance cũ và làm
   * mất tác dụng của Pessimistic Locking (overdraft race condition).
   */
  private Money computeBalanceFromDb(UUID accountId, Currency currency) {
    BigDecimal totalCredit = ledgerEntryRepository.sumCreditByAccountId(accountId);
    BigDecimal totalDebit = ledgerEntryRepository.sumDebitByAccountId(accountId);

    return Money.of(totalCredit, currency).subtract(Money.of(totalDebit, currency));
  }

  @Transactional
  public void withdraw(UUID accountId, UUID counterpartyAccountId, Money amount, String createdBy) {
    Account account = accountRepository.findByIdForUpdate(accountId)
      .orElseThrow(() -> new IllegalArgumentException("Account not found: " + accountId));

    if (!account.getCurrency().equals(amount.getCurrency())) {
      throw new CurrencyMismatchException(account.getCurrency(), amount.getCurrency());
    }

    Money currentBalance = computeBalanceFromDb(accountId, account.getCurrency());

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