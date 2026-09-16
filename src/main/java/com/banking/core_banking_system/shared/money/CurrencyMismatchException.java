package com.banking.core_banking_system.shared.money;

import java.util.Currency;

public class CurrencyMismatchException extends IllegalArgumentException {

  public CurrencyMismatchException(Currency expected, Currency actual) {
    super("Currency mismatch: expected=" + expected.getCurrencyCode() + ", actual=" + actual.getCurrencyCode());
  }
}
