package com.banking.core_banking_system.compliance;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface ComplianceRecordRepository extends JpaRepository<ComplianceRecord, UUID> {

  List<ComplianceRecord> findByTransactionId(UUID transactionId);
}
