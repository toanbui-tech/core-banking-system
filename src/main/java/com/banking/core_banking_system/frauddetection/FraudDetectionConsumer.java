package com.banking.core_banking_system.frauddetection;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.math.BigDecimal;

/**
 * Proof-of-concept: chỉ chứng minh kiến trúc publish/subscribe hoạt động, không có
 * logic nghiệp vụ thật (không có rule phát hiện gian lận thật).
 */
@Component
public class FraudDetectionConsumer {

  private static final Logger log = LoggerFactory.getLogger(FraudDetectionConsumer.class);

  private final ObjectMapper objectMapper;

  public FraudDetectionConsumer(ObjectMapper objectMapper) {
    this.objectMapper = objectMapper;
  }

  @KafkaListener(topics = "transaction-posted-topic", groupId = "fraud-detection-group")
  public void onMessage(String payload) {
    JsonNode node = objectMapper.readTree(payload);
    String transactionId = node.path("transactionId").asString();
    BigDecimal amount = totalDebitAmount(node.path("entries"));

    log.info("[Fraud Detection] Would send alert for transaction {}, amount {}", transactionId, amount);
  }

  private BigDecimal totalDebitAmount(JsonNode entries) {
    BigDecimal total = BigDecimal.ZERO;
    for (JsonNode entry : entries) {
      if ("DEBIT".equals(entry.path("entryType").asString())) {
        total = total.add(entry.path("amount").decimalValue());
      }
    }
    return total;
  }
}
