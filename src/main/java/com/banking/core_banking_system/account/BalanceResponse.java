package com.banking.core_banking_system.account;

import com.banking.core_banking_system.shared.money.Money;

import java.math.BigDecimal;
import java.util.UUID;

public record BalanceResponse(UUID accountId, BigDecimal amount, String currency) {

  public static BalanceResponse of(UUID accountId, Money balance) {
    return new BalanceResponse(accountId, balance.getAmount(), balance.getCurrency().getCurrencyCode());
  }
}
