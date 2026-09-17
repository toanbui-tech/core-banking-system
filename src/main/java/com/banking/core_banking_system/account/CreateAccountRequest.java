package com.banking.core_banking_system.account;

public record CreateAccountRequest(String accountNumber, String accountType, String currency) {
}
