package com.banking.core_banking_system.account;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * Dùng chung cho cả deposit và withdraw - 2 request có cùng shape
 * (chỉ khác entry_type do AccountService quyết định, không phải do client truyền).
 */
public record TransferRequest(UUID counterpartyAccountId, BigDecimal amount, String currency, String createdBy) {
}
