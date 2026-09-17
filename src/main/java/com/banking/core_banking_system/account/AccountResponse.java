package com.banking.core_banking_system.account;

import java.util.UUID;

public record AccountResponse(UUID id, String accountNumber, String accountType, String currency, String status) {

  public static AccountResponse from(Account account) {
    return new AccountResponse(
      account.getId(),
      account.getAccountNumber(),
      account.getAccountType(),
      account.getCurrency().getCurrencyCode(),
      account.getStatus()
    );
  }
}
