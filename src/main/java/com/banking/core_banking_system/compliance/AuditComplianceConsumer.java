package com.banking.core_banking_system.compliance;

import com.banking.core_banking_system.ledger.event.LedgerEntrySnapshot;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.util.List;
import java.util.UUID;

@Component
public class AuditComplianceConsumer {

  private static final Logger log = LoggerFactory.getLogger(AuditComplianceConsumer.class);

  private final AuditComplianceService auditComplianceService;
  private final ObjectMapper objectMapper;

  public AuditComplianceConsumer(AuditComplianceService auditComplianceService, ObjectMapper objectMapper) {
    this.auditComplianceService = auditComplianceService;
    this.objectMapper = objectMapper;
  }

  @KafkaListener(topics = "transaction-posted-topic", groupId = "audit-compliance-group")
  public void onMessage(String payload) {
    JsonNode node = objectMapper.readTree(payload);
    UUID eventId = UUID.fromString(node.path("eventId").asString());
    UUID transactionId = UUID.fromString(node.path("transactionId").asString());
    String createdBy = node.path("createdBy").asString();
    String eventType = node.path("eventType").asString();
    List<LedgerEntrySnapshot> entries = objectMapper.convertValue(
      node.path("entries"),
      new TypeReference<List<LedgerEntrySnapshot>>() {
      }
    );

    log.info("[Audit/Compliance] Nhận {} (eventId={}) cho transaction {}", eventType, eventId, transactionId);
    auditComplianceService.recordCompliance(eventId, transactionId, createdBy, entries);
  }
}
