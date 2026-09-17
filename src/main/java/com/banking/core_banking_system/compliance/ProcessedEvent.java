package com.banking.core_banking_system.compliance;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PostLoad;
import jakarta.persistence.PostPersist;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import jakarta.persistence.Transient;
import lombok.Getter;
import org.springframework.data.domain.Persistable;

import java.time.LocalDateTime;
import java.util.Objects;
import java.util.UUID;

/**
 * Đánh dấu 1 event (theo eventId) đã được AuditComplianceConsumer xử lý,
 * dùng để đảm bảo idempotency trước tình huống Kafka gửi lại message (at-least-once delivery).
 */
@Entity
@Table(name = "processed_events")
@Getter
public class ProcessedEvent implements Persistable<UUID> {

  @Id
  private UUID eventId;

  @Transient
  private boolean isNew = true;

  @Column(name = "processed_at", nullable = false, updatable = false)
  private LocalDateTime processedAt;

  protected ProcessedEvent() {
    // required by JPA
  }

  private ProcessedEvent(UUID eventId) {
    this.eventId = Objects.requireNonNull(eventId, "eventId must not be null");
  }

  public static ProcessedEvent create(UUID eventId) {
    return new ProcessedEvent(eventId);
  }

  @PrePersist
  protected void onCreate() {
    this.processedAt = LocalDateTime.now();
  }

  @Override
  public UUID getId() {
    return eventId;
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
