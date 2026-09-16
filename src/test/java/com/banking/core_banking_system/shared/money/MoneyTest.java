package com.banking.core_banking_system.shared.money;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MoneyTest {

  @Test
  void add_shouldSumAmounts_whenSameCurrency() {
    Money a = Money.of(new BigDecimal("100.00"), "VND");
    Money b = Money.of(new BigDecimal("50.00"), "VND");

    Money result = a.add(b);

    assertEquals(Money.of(new BigDecimal("150.00"), "VND"), result);
  }

  @Test
  void add_shouldThrow_whenDifferentCurrency() {
    Money vnd = Money.of(new BigDecimal("100.00"), "VND");
    Money usd = Money.of(new BigDecimal("50.00"), "USD");

    assertThrows(CurrencyMismatchException.class, () -> vnd.add(usd));
  }

  @Test
  void subtract_shouldReduceAmount_whenSameCurrency() {
    Money a = Money.of(new BigDecimal("100.00"), "VND");
    Money b = Money.of(new BigDecimal("30.00"), "VND");

    Money result = a.subtract(b);

    assertEquals(Money.of(new BigDecimal("70.00"), "VND"), result);
  }

  @Test
  void subtract_shouldThrow_whenDifferentCurrency() {
    Money vnd = Money.of(new BigDecimal("100.00"), "VND");
    Money usd = Money.of(new BigDecimal("50.00"), "USD");

    assertThrows(CurrencyMismatchException.class, () -> vnd.subtract(usd));
  }

  @Test
  void add_shouldNotMutateOriginalInstances() {
    Money a = Money.of(new BigDecimal("100.00"), "VND");
    Money b = Money.of(new BigDecimal("50.00"), "VND");

    a.add(b);

    assertEquals(Money.of(new BigDecimal("100.00"), "VND"), a);
    assertEquals(Money.of(new BigDecimal("50.00"), "VND"), b);
  }

  @Test
  void equals_shouldIgnoreScaleDifferences_whenSameNumericValue() {
    Money a = Money.of(new BigDecimal("100.00"), "VND");
    Money b = Money.of(new BigDecimal("100.0"), "VND");

    assertEquals(a, b);
  }

  @Test
  void isLessThan_shouldCompareAmounts_whenSameCurrency() {
    Money smaller = Money.of(new BigDecimal("30.00"), "VND");
    Money bigger = Money.of(new BigDecimal("80.00"), "VND");

    assertTrue(smaller.isLessThan(bigger));
    assertFalse(bigger.isLessThan(smaller));
  }

  @Test
  void isLessThan_shouldThrow_whenDifferentCurrency() {
    Money vnd = Money.of(new BigDecimal("30.00"), "VND");
    Money usd = Money.of(new BigDecimal("80.00"), "USD");

    assertThrows(CurrencyMismatchException.class, () -> vnd.isLessThan(usd));
  }
}
