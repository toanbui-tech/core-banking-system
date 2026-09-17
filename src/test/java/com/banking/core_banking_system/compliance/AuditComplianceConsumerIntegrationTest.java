package com.banking.core_banking_system.compliance;

import com.banking.core_banking_system.ledger.EntryType;
import com.banking.core_banking_system.ledger.event.LedgerEntrySnapshot;
import com.banking.core_banking_system.ledger.event.TransactionPostedEvent;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.kafka.KafkaContainer;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ObjectNode;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Test end-to-end qua Kafka thật (Testcontainers) cho AuditComplianceConsumer:
 * publish message trực tiếp lên topic rồi assert dữ liệu được ghi nhận + idempotent
 * khi cùng 1 eventId được gửi lại (mô phỏng at-least-once delivery của Kafka).
 */
@SpringBootTest(properties = "outbox.publisher.enabled=false")
@Testcontainers
class AuditComplianceConsumerIntegrationTest {

  @Container
  static final KafkaContainer KAFKA = new KafkaContainer("apache/kafka:3.8.0");

  @DynamicPropertySource
  static void kafkaProperties(DynamicPropertyRegistry registry) {
    registry.add("spring.kafka.bootstrap-servers", KAFKA::getBootstrapServers);
  }

  @Autowired
  private KafkaTemplate<String, Object> kafkaTemplate;

  @Autowired
  private ProcessedEventRepository processedEventRepository;

  @Autowired
  private ComplianceRecordRepository complianceRecordRepository;

  @Autowired
  private ObjectMapper objectMapper;

  private ObjectNode buildTransactionPostedPayload(UUID eventId, UUID transactionId, UUID accountA, UUID accountB) {
    TransactionPostedEvent event = new TransactionPostedEvent(
      eventId,
      transactionId,
      "test-user",
      LocalDateTime.now(),
      List.of(
        new LedgerEntrySnapshot(accountA, EntryType.DEBIT, new BigDecimal("100.00"), "VND"),
        new LedgerEntrySnapshot(accountB, EntryType.CREDIT, new BigDecimal("100.00"), "VND")
      )
    );
    ObjectNode node = (ObjectNode) objectMapper.valueToTree(event);
    node.put("eventType", "TransactionPostedEvent");
    return node;
  }

  @Test
  void shouldRecordOneComplianceRecordPerEntry_whenTransactionPostedEventConsumed() {
    UUID eventId = UUID.randomUUID();
    UUID transactionId = UUID.randomUUID();
    UUID accountA = UUID.randomUUID();
    UUID accountB = UUID.randomUUID();

    kafkaTemplate.send(
      "transaction-posted-topic",
      transactionId.toString(),
      buildTransactionPostedPayload(eventId, transactionId, accountA, accountB)
    );

    await().atMost(Duration.ofSeconds(15)).untilAsserted(() -> {
      List<ComplianceRecord> records = complianceRecordRepository.findByTransactionId(transactionId);
      assertEquals(2, records.size());
      assertTrue(records.stream().anyMatch(r -> r.getAccountId().equals(accountA) && r.getEntryType() == EntryType.DEBIT));
      assertTrue(records.stream().anyMatch(r -> r.getAccountId().equals(accountB) && r.getEntryType() == EntryType.CREDIT));
      assertTrue(processedEventRepository.existsById(eventId));
    });
  }

  @Test
  void shouldNotDuplicateComplianceRecords_whenSameEventDeliveredTwice() {
    UUID eventId = UUID.randomUUID();
    UUID transactionId = UUID.randomUUID();
    ObjectNode payload = buildTransactionPostedPayload(eventId, transactionId, UUID.randomUUID(), UUID.randomUUID());

    // giả lập Kafka gửi lại đúng event (at-least-once delivery, VD do consumer crash trước khi commit offset)
    kafkaTemplate.send("transaction-posted-topic", transactionId.toString(), payload);
    kafkaTemplate.send("transaction-posted-topic", transactionId.toString(), payload);

    await().atMost(Duration.ofSeconds(15)).untilAsserted(() ->
      assertEquals(2, complianceRecordRepository.findByTransactionId(transactionId).size())
    );

    // giữ nguyên trạng thái ổn định thêm vài giây để chắc chắn message thứ 2 (duplicate)
    // không tạo thêm compliance_records nào
    await().during(Duration.ofSeconds(3)).atMost(Duration.ofSeconds(10)).untilAsserted(() ->
      assertEquals(2, complianceRecordRepository.findByTransactionId(transactionId).size())
    );
  }
}
