package com.banking.core_banking_system.ledger;

public enum EntryType {
  DEBIT,
  CREDIT;

  public EntryType opposite() {
    return this == DEBIT ? CREDIT : DEBIT;
  }
}