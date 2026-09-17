package com.banking.core_banking_system.compliance;

import com.banking.core_banking_system.ledger.event.LedgerEntrySnapshot;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
public class AuditComplianceService {

  private static final Logger log = LoggerFactory.getLogger(AuditComplianceService.class);

  private final ProcessedEventRepository processedEventRepository;
  private final ComplianceRecordRepository complianceRecordRepository;

  public AuditComplianceService(
    ProcessedEventRepository processedEventRepository,
    ComplianceRecordRepository complianceRecordRepository
  ) {
    this.processedEventRepository = processedEventRepository;
    this.complianceRecordRepository = complianceRecordRepository;
  }

  /**
   * Ghi nhận compliance record cho từng LedgerEntry của event. Idempotent: nếu eventId
   * đã được xử lý trước đó (Kafka gửi lại message do at-least-once delivery), bỏ qua.
   */
  @Transactional
  public void recordCompliance(UUID eventId, UUID transactionId, String createdBy, List<LedgerEntrySnapshot> entries) {
    if (processedEventRepository.existsById(eventId)) {
      log.info("Event {} đã được xử lý trước đó, bỏ qua (idempotent)", eventId);
      return;
    }

    for (LedgerEntrySnapshot entry : entries) {
      complianceRecordRepository.save(ComplianceRecord.create(
        transactionId,
        entry.accountId(),
        entry.entryType(),
        entry.amount(),
        entry.currency(),
        createdBy
      ));
    }
    processedEventRepository.save(ProcessedEvent.create(eventId));
  }
}
