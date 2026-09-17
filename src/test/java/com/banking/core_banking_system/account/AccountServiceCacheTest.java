package com.banking.core_banking_system.account;

import com.banking.core_banking_system.ledger.LedgerService;
import com.banking.core_banking_system.ledger.LedgerEntryRepository;
import com.banking.core_banking_system.shared.money.Money;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.Currency;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AccountServiceCacheTest {

  @Mock
  private AccountRepository accountRepository;

  @Mock
  private LedgerEntryRepository ledgerEntryRepository;

  @Mock
  private LedgerService ledgerService;

  @Mock
  private AccountBalanceCache balanceCache;

  private AccountService accountService;

  @BeforeEach
  void setUp() {
    accountService = new AccountService(accountRepository, ledgerEntryRepository, ledgerService, balanceCache);
  }

  private Account accountWith(UUID id, String currencyCode) {
    Account account = new Account();
    account.setId(id);
    account.setCurrency(Currency.getInstance(currencyCode));
    return account;
  }

  @Test
  void getBalance_shouldReturnCachedValue_andSkipDbQueries_onCacheHit() {
    UUID accountId = UUID.randomUUID();
    when(accountRepository.findById(accountId)).thenReturn(Optional.of(accountWith(accountId, "VND")));
    when(balanceCache.get(accountId)).thenReturn(Optional.of(new BigDecimal("500.00")));

    Money balance = accountService.getBalance(accountId);

    assertEquals(Money.of(new BigDecimal("500.00"), "VND"), balance);
    verifyNoInteractions(ledgerEntryRepository);
  }

  @Test
  void getBalance_shouldComputeFromDbAndPopulateCache_onCacheMiss() {
    UUID accountId = UUID.randomUUID();
    when(accountRepository.findById(accountId)).thenReturn(Optional.of(accountWith(accountId, "VND")));
    when(balanceCache.get(accountId)).thenReturn(Optional.empty());
    when(ledgerEntryRepository.sumCreditByAccountId(accountId)).thenReturn(new BigDecimal("1000.00"));
    when(ledgerEntryRepository.sumDebitByAccountId(accountId)).thenReturn(new BigDecimal("300.00"));

    Money balance = accountService.getBalance(accountId);

    assertEquals(Money.of(new BigDecimal("700.00"), "VND"), balance);
    verify(balanceCache).put(accountId, new BigDecimal("700.00"));
  }

  @Test
  void withdraw_shouldNeverReadBalanceFromCache_toPreserveOverdraftInvariant() {
    UUID accountId = UUID.randomUUID();
    UUID counterpartyId = UUID.randomUUID();
    Account account = accountWith(accountId, "VND");

    when(accountRepository.findByIdForUpdate(accountId)).thenReturn(Optional.of(account));
    when(ledgerEntryRepository.sumCreditByAccountId(accountId)).thenReturn(new BigDecimal("1000.00"));
    when(ledgerEntryRepository.sumDebitByAccountId(accountId)).thenReturn(new BigDecimal("0.00"));

    accountService.withdraw(accountId, counterpartyId, Money.of(new BigDecimal("100.00"), "VND"), "test-user");

    // withdraw() kiểm tra invariant Debit<=Credit trong pessimistic lock -> PHẢI đọc thẳng DB,
    // không được đi qua balanceCache (có thể trả về balance cũ và làm mất tác dụng của lock)
    verify(balanceCache, never()).get(any());
  }
}
