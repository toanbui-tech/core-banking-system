package com.banking.core_banking_system.outbox;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;
import tools.jackson.databind.ObjectMapper;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class OutboxEventPublisherTest {

  @Mock
  private OutboxEventRepository outboxEventRepository;

  @Mock
  private KafkaTemplate<String, Object> kafkaTemplate;

  private final ObjectMapper objectMapper = new ObjectMapper();

  private OutboxEventPublisher publisher;

  @BeforeEach
  void setUp() {
    publisher = new OutboxEventPublisher(outboxEventRepository, kafkaTemplate, objectMapper);
  }

  @Test
  void publishPendingEvents_shouldSendToKafkaAndMarkPublished_whenSendSucceeds() {
    UUID transactionId = UUID.randomUUID();
    OutboxEvent event = OutboxEvent.create(
      transactionId,
      "TransactionPostedEvent",
      "{\"transactionId\":\"" + transactionId + "\"}"
    );
    when(outboxEventRepository.findByPublishedAtIsNullOrderByCreatedAtAsc()).thenReturn(List.of(event));
    when(kafkaTemplate.send(eq(OutboxEventPublisher.TOPIC), eq(transactionId.toString()), any()))
      .thenReturn(CompletableFuture.completedFuture(mock(SendResult.class)));

    publisher.publishPendingEvents();

    ArgumentCaptor<OutboxEvent> savedCaptor = ArgumentCaptor.forClass(OutboxEvent.class);
    verify(outboxEventRepository).save(savedCaptor.capture());
    assertNotNull(savedCaptor.getValue().getPublishedAt());
    assertEquals(event.getId(), savedCaptor.getValue().getId());
  }

  @Test
  void publishPendingEvents_shouldNotMarkPublished_whenKafkaSendFails() {
    UUID transactionId = UUID.randomUUID();
    OutboxEvent event = OutboxEvent.create(
      transactionId,
      "TransactionPostedEvent",
      "{\"transactionId\":\"" + transactionId + "\"}"
    );
    when(outboxEventRepository.findByPublishedAtIsNullOrderByCreatedAtAsc()).thenReturn(List.of(event));

    CompletableFuture<SendResult<String, Object>> failed = new CompletableFuture<>();
    failed.completeExceptionally(new RuntimeException("broker không khả dụng"));
    when(kafkaTemplate.send(eq(OutboxEventPublisher.TOPIC), eq(transactionId.toString()), any()))
      .thenReturn(failed);

    publisher.publishPendingEvents();

    verify(outboxEventRepository, never()).save(any());
  }

  @Test
  void publishPendingEvents_shouldDoNothing_whenNoPendingEvents() {
    when(outboxEventRepository.findByPublishedAtIsNullOrderByCreatedAtAsc()).thenReturn(List.of());

    publisher.publishPendingEvents();

    verify(kafkaTemplate, never()).send(any(String.class), any(), any());
    verify(outboxEventRepository, never()).save(any());
  }
}
