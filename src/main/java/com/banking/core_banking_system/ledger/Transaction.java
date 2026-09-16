package com.banking.core_banking_system.ledger;

import com.banking.core_banking_system.shared.money.Money;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import jakarta.persistence.PostLoad;
import jakarta.persistence.PostPersist;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import jakarta.persistence.Transient;
import lombok.Getter;
import org.springframework.data.domain.Persistable;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

@Entity
@Table(name = "transactions")
@Getter
public class Transaction implements Persistable<UUID> {

  @Id
  private UUID id;

  @Transient
  private boolean isNew = true;

  @Column(name = "created_by", nullable = false, updatable = false)
  private String createdBy;

  @Column(name = "created_at", nullable = false, updatable = false)
  private LocalDateTime createdAt;

  @Column(name = "reversal_of_transaction_id")
  private UUID reversalOfTransactionId;

  @OneToMany(mappedBy = "transaction", cascade = CascadeType.PERSIST)
  private List<LedgerEntry> entries = new ArrayList<>();

  protected Transaction() {
    // required by JPA
  }

  private Transaction(String createdBy, UUID reversalOfTransactionId) {
    this.id = UUID.randomUUID();
    this.createdBy = Objects.requireNonNull(createdBy, "createdBy must not be null");
    this.reversalOfTransactionId = reversalOfTransactionId;
  }

  @PrePersist
  protected void onCreate() {
    this.createdAt = LocalDateTime.now();
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

  public static Transaction record(List<LedgerEntry> entries, String createdBy) {
    Transaction transaction = new Transaction(createdBy, null);
    entries.forEach(transaction::addEntry);
    transaction.validateBalanced();
    return transaction;
  }

  public Transaction reverse(String reversedBy) {
    Transaction reversal = new Transaction(reversedBy, this.id);

    for (LedgerEntry original : this.entries) {
      LedgerEntry reversalEntry = new LedgerEntry();
      reversalEntry.setAccountId(original.getAccountId());
      reversalEntry.setEntryType(original.getEntryType().opposite());
      reversalEntry.setAmount(original.getAmount());
      reversalEntry.setReversalOfEntryId(original.getId());
      reversal.addEntry(reversalEntry);
    }

    reversal.validateBalanced();
    return reversal;
  }

  public List<LedgerEntry> getEntries() {
    return List.copyOf(entries);
  }

  private void addEntry(LedgerEntry entry) {
    entry.setTransaction(this);
    entry.setCreatedBy(this.createdBy);
    this.entries.add(entry);
  }

  private void validateBalanced() {
    if (entries.isEmpty()) {
      throw new IllegalArgumentException("Giao dịch phải có ít nhất 1 bút toán");
    }

    Money zero = Money.zero(entries.get(0).getAmount().getCurrency());

    Money totalDebit = entries.stream()
      .filter(e -> e.getEntryType() == EntryType.DEBIT)
      .map(LedgerEntry::getAmount)
      .reduce(zero, Money::add);

    Money totalCredit = entries.stream()
      .filter(e -> e.getEntryType() == EntryType.CREDIT)
      .map(LedgerEntry::getAmount)
      .reduce(zero, Money::add);

    if (totalDebit.getAmount().compareTo(totalCredit.getAmount()) != 0) {
      throw new IllegalStateException(
        "Giao dịch không cân bằng: Nợ=" + totalDebit + ", Có=" + totalCredit
      );
    }
  }
}
