package com.banking.core_banking_system.outbox;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ObjectNode;

import java.util.List;

/**
 * Đọc các OutboxEvent chưa publish (published_at = null) và gửi lên Kafka.
 * Bị vô hiệu hóa trong test (outbox.publisher.enabled=false) để test không cần broker thật.
 */
@Component
@ConditionalOnProperty(prefix = "outbox.publisher", name = "enabled", havingValue = "true", matchIfMissing = true)
public class OutboxEventPublisher {

  private static final Logger log = LoggerFactory.getLogger(OutboxEventPublisher.class);
  static final String TOPIC = "transaction-posted-topic";

  private final OutboxEventRepository outboxEventRepository;
  private final KafkaTemplate<String, Object> kafkaTemplate;
  private final ObjectMapper objectMapper;

  public OutboxEventPublisher(
    OutboxEventRepository outboxEventRepository,
    KafkaTemplate<String, Object> kafkaTemplate,
    ObjectMapper objectMapper
  ) {
    this.outboxEventRepository = outboxEventRepository;
    this.kafkaTemplate = kafkaTemplate;
    this.objectMapper = objectMapper;
  }

  @Scheduled(fixedDelayString = "${outbox.publisher.fixed-delay-ms:5000}")
  @Transactional
  public void publishPendingEvents() {
    List<OutboxEvent> pending = outboxEventRepository.findByPublishedAtIsNullOrderByCreatedAtAsc();
    for (OutboxEvent event : pending) {
      publish(event);
    }
  }

  private void publish(OutboxEvent event) {
    try {
      JsonNode payload = objectMapper.readTree(event.getPayload());
      // nhúng eventType vào payload để consumer tự phân biệt được loại event trên cùng 1 topic,
      // vì outbox_events.event_type không được truyền kèm message (chỉ tồn tại trong DB)
      if (payload instanceof ObjectNode objectNode) {
        objectNode.put("eventType", event.getEventType());
      }
      // dùng transactionId (aggregateId) làm key để Kafka giữ thứ tự các event của cùng 1 transaction
      kafkaTemplate.send(TOPIC, event.getAggregateId().toString(), payload).get();
      event.markPublished();
      outboxEventRepository.save(event);
    } catch (Exception e) {
      log.error("Không thể publish OutboxEvent id={} lên topic {}, sẽ thử lại ở lần chạy sau", event.getId(), TOPIC, e);
    }
  }
}
