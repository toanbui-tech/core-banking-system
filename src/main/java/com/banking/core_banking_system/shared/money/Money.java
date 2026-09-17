package com.banking.core_banking_system.shared.money;

import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.Embeddable;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Currency;
import java.util.Objects;

@Embeddable
public final class Money {

  /**
   * Scale cố định cho MỌI currency (kể cả VND), không dùng Currency.getDefaultFractionDigits()
   * (ISO 4217 nói VND=0). Core banking domain này giữ scale=2 xuyên suốt ở tầng lưu trữ/tính toán
   * để không mất độ chính xác ở các phép tính trung gian (lãi suất, phí theo %) — làm tròn theo
   * đơn vị hiển thị thực tế (nếu cần) là việc của tầng presentation, không phải Money.
   */
  private static final int SCALE = 2;

  @Column(name = "amount", nullable = false, precision = 19, scale = 2)
  private BigDecimal amount;

  @Convert(converter = CurrencyConverter.class)
  @Column(name = "currency", nullable = false, length = 3)
  private Currency currency;

  protected Money() {
    // required by JPA
  }

  private Money(BigDecimal amount, Currency currency) {
    Objects.requireNonNull(amount, "amount must not be null");
    this.currency = Objects.requireNonNull(currency, "currency must not be null");
    // RoundingMode.UNNECESSARY: nếu ném exception ở đây nghĩa là có chỗ đang tạo Money với giá trị
    // không tròn ở scale=2 — đó là lỗi cần sửa ở nơi gọi, không phải lý do để đổi RoundingMode.
    this.amount = amount.setScale(SCALE, RoundingMode.UNNECESSARY);
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
