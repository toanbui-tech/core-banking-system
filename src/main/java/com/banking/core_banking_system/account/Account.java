package com.banking.core_banking_system.account;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "accounts")
@Getter
@Setter
public class Account {

  @Id
  @GeneratedValue
  private UUID id;

  @Column(name = "account_number", nullable = false, unique = true)
  private String accountNumber;

  @Column(name = "account_type", nullable = false)
  private String accountType;

  @Column(nullable = false)
  private String currency;

  @Column(nullable = false)
  private String status;

  @Column(name = "created_at", nullable = false, updatable = false)
  private LocalDateTime createdAt;

  @PrePersist
  protected void onCreate() {
    this.createdAt = LocalDateTime.now();
  }
}