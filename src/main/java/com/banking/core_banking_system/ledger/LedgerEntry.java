package com.banking.core_banking_system.ledger;

import com.banking.core_banking_system.shared.money.Money;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "ledger_entries")
@Getter
@Setter
public class LedgerEntry {

  @Id
  @GeneratedValue
  private UUID id;

  @Column(name = "account_id", nullable = false)
  private UUID accountId;

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "transaction_id", nullable = false, updatable = false)
  private Transaction transaction;

  @Enumerated(EnumType.STRING)
  @Column(name = "entry_type", nullable = false)
  private EntryType entryType;

  @Embedded
  private Money amount;

  @Column(name = "created_by", nullable = false, updatable = false)
  private String createdBy;

  @Column(name = "reversal_of_entry_id")
  private UUID reversalOfEntryId;

  @Column(name = "created_at", nullable = false, updatable = false)
  private LocalDateTime createdAt;

  @PrePersist
  protected void onCreate() {
    this.createdAt = LocalDateTime.now();
  }

  public UUID getTransactionId() {
    return transaction != null ? transaction.getId() : null;
  }
}