package com.banking.core_banking_system.compliance;

import com.banking.core_banking_system.ledger.EntryType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.PostLoad;
import jakarta.persistence.PostPersist;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import jakarta.persistence.Transient;
import lombok.Getter;
import org.springframework.data.domain.Persistable;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Objects;
import java.util.UUID;

/**
 * Bản ghi mô phỏng việc đẩy dữ liệu giao dịch sang hệ thống compliance tách biệt,
 * 1 dòng cho mỗi LedgerEntry của Transaction (không gộp theo transaction) để giữ
 * đủ chi tiết account/entry_type phục vụ tra soát.
 */
@Entity
@Table(name = "compliance_records")
@Getter
public class ComplianceRecord implements Persistable<UUID> {

  @Id
  private UUID id;

  @Transient
  private boolean isNew = true;

  @Column(name = "transaction_id", nullable = false, updatable = false)
  private UUID transactionId;

  @Column(name = "account_id", nullable = false, updatable = false)
  private UUID accountId;

  @Enumerated(EnumType.STRING)
  @Column(name = "entry_type", nullable = false, updatable = false)
  private EntryType entryType;

  @Column(name = "amount", nullable = false, updatable = false, precision = 19, scale = 2)
  private BigDecimal amount;

  @Column(name = "currency", nullable = false, updatable = false, length = 3)
  private String currency;

  @Column(name = "created_by", nullable = false, updatable = false)
  private String createdBy;

  @Column(name = "recorded_at", nullable = false, updatable = false)
  private LocalDateTime recordedAt;

  protected ComplianceRecord() {
    // required by JPA
  }

  private ComplianceRecord(
    UUID transactionId,
    UUID accountId,
    EntryType entryType,
    BigDecimal amount,
    String currency,
    String createdBy
  ) {
    this.id = UUID.randomUUID();
    this.transactionId = Objects.requireNonNull(transactionId, "transactionId must not be null");
    this.accountId = Objects.requireNonNull(accountId, "accountId must not be null");
    this.entryType = Objects.requireNonNull(entryType, "entryType must not be null");
    this.amount = Objects.requireNonNull(amount, "amount must not be null");
    this.currency = Objects.requireNonNull(currency, "currency must not be null");
    this.createdBy = Objects.requireNonNull(createdBy, "createdBy must not be null");
  }

  public static ComplianceRecord create(
    UUID transactionId,
    UUID accountId,
    EntryType entryType,
    BigDecimal amount,
    String currency,
    String createdBy
  ) {
    return new ComplianceRecord(transactionId, accountId, entryType, amount, currency, createdBy);
  }

  @PrePersist
  protected void onCreate() {
    this.recordedAt = LocalDateTime.now();
  }

  @Override
  public boolean isNew() {
    return isNew;
  }

  @PostLoad
  @PostPersist
  protected void markNotNew() {
    this.isNew = false;
  }
}
