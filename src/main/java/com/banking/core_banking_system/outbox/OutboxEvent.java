package com.banking.core_banking_system.outbox;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PostLoad;
import jakarta.persistence.PostPersist;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import jakarta.persistence.Transient;
import lombok.Getter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import org.springframework.data.domain.Persistable;

import java.time.LocalDateTime;
import java.util.Objects;
import java.util.UUID;

@Entity
@Table(name = "outbox_events")
@Getter
public class OutboxEvent implements Persistable<UUID> {

  @Id
  private UUID id;

  @Transient
  private boolean isNew = true;

  @Column(name = "aggregate_id", nullable = false, updatable = false)
  private UUID aggregateId;

  @Column(name = "event_type", nullable = false, updatable = false)
  private String eventType;

  @JdbcTypeCode(SqlTypes.JSON)
  @Column(name = "payload", nullable = false, updatable = false, columnDefinition = "jsonb")
  private String payload;

  @Column(name = "created_at", nullable = false, updatable = false)
  private LocalDateTime createdAt;

  @Column(name = "published_at")
  private LocalDateTime publishedAt;

  protected OutboxEvent() {
    // required by JPA
  }

  private OutboxEvent(UUID aggregateId, String eventType, String payload) {
    this.id = UUID.randomUUID();
    this.aggregateId = Objects.requireNonNull(aggregateId, "aggregateId must not be null");
    this.eventType = Objects.requireNonNull(eventType, "eventType must not be null");
    this.payload = Objects.requireNonNull(payload, "payload must not be null");
  }

  public static OutboxEvent create(UUID aggregateId, String eventType, String payload) {
    return new OutboxEvent(aggregateId, eventType, payload);
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

  public boolean isPublished() {
    return publishedAt != null;
  }

  public void markPublished() {
    this.publishedAt = LocalDateTime.now();
  }
}
