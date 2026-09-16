package com.banking.core_banking_system.shared.money;

import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.Embeddable;

import java.math.BigDecimal;
import java.util.Currency;
import java.util.Objects;

@Embeddable
public final class Money {

  @Column(name = "amount", nullable = false, precision = 19, scale = 2)
  private BigDecimal amount;

  @Convert(converter = CurrencyConverter.class)
  @Column(name = "currency", nullable = false, length = 3)
  private Currency currency;

  protected Money() {
    // required by JPA
  }

  private Money(BigDecimal amount, Currency currency) {
    this.amount = Objects.requireNonNull(amount, "amount must not be null");
    this.currency = Objects.requireNonNull(currency, "currency must not be null");
  }

  public static Money of(BigDecimal amount, Currency currency) {
    return new Money(amount, currency);
  }

  public static Money of(BigDecimal amount, String currencyCode) {
    return new Money(amount, Currency.getInstance(currencyCode));
  }

  public static Money zero(Currency currency) {
    return new Money(BigDecimal.ZERO, currency);
  }

  public Money add(Money other) {
    requireSameCurrency(other);
    return new Money(this.amount.add(other.amount), this.currency);
  }

  public Money subtract(Money other) {
    requireSameCurrency(other);
    return new Money(this.amount.subtract(other.amount), this.currency);
  }

  public boolean isLessThan(Money other) {
    requireSameCurrency(other);
    return this.amount.compareTo(other.amount) < 0;
  }

  public boolean isNegative() {
    return this.amount.signum() < 0;
  }

  private void requireSameCurrency(Money other) {
    if (!this.currency.equals(other.currency)) {
      throw new CurrencyMismatchException(this.currency, other.currency);
    }
  }

  public BigDecimal getAmount() {
    return amount;
  }

  public Currency getCurrency() {
    return currency;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (!(o instanceof Money other)) return false;
    return amount.compareTo(other.amount) == 0 && currency.equals(other.currency);
  }

  @Override
  public int hashCode() {
    return Objects.hash(amount.stripTrailingZeros(), currency);
  }

  @Override
  public String toString() {
    return amount.toPlainString() + " " + currency.getCurrencyCode();
  }
}
