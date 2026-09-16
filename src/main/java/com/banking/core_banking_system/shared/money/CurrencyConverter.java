package com.banking.core_banking_system.shared.money;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

import java.util.Currency;

@Converter(autoApply = false)
public class CurrencyConverter implements AttributeConverter<Currency, String> {

  @Override
  public String convertToDatabaseColumn(Currency attribute) {
    return attribute == null ? null : attribute.getCurrencyCode();
  }

  @Override
  public Currency convertToEntityAttribute(String dbData) {
    return dbData == null ? null : Currency.getInstance(dbData);
  }
}
