package com.banking.core_banking_system.compliance;

import com.banking.core_banking_system.account.Account;
import com.banking.core_banking_system.account.AccountRepository;
import com.banking.core_banking_system.ledger.EntryType;
import com.banking.core_banking_system.ledger.LedgerEntry;
import com.banking.core_banking_system.ledger.LedgerService;
import com.banking.core_banking_system.ledger.Transaction;
import com.banking.core_banking_system.ledger.TransactionRepository;
import com.banking.core_banking_system.shared.money.Money;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.kafka.KafkaContainer;

import java.math.BigDecimal;
import java.time.Duration;
import java.util.Currency;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Test khép kín toàn bộ pipeline thật: LedgerService.recordTransaction() -> OutboxEvent ->
 * OutboxEventPublisher (bật thật, không mock) -> Kafka -> AuditComplianceConsumer -> compliance_records.
 * Khác với AuditComplianceConsumerIntegrationTest (publish message giả lập trực tiếp),
 * test này xác nhận Bước 1 và Bước 2 thực sự ăn khớp với nhau qua toàn bộ luồng nghiệp vụ.
 */
@SpringBootTest(properties = "outbox.publisher.fixed-delay-ms=500")
@Testcontainers
class TransactionToComplianceEndToEndTest {

  @Container
  static final KafkaContainer KAFKA = new KafkaContainer("apache/kafka:3.8.0");

  @DynamicPropertySource
  static void kafkaProperties(DynamicPropertyRegistry registry) {
    registry.add("spring.kafka.bootstrap-servers", KAFKA::getBootstrapServers);
  }

  @Autowired
  private LedgerService ledgerService;

  @Autowired
  private AccountRepository accountRepository;

  @Autowired
  private ComplianceRecordRepository complianceRecordRepository;

  @Autowired
  private TransactionRepository transactionRepository;

  private UUID createTestAccount(String prefix) {
    Account account = new Account();
    account.setAccountNumber(prefix + UUID.randomUUID().toString().substring(0, 8));
    account.setAccountType("CASH");
    account.setCurrency(Currency.getInstance("VND"));
    account.setStatus("ACTIVE");
    return accountRepository.save(account).getId();
  }

  @Test
  void recordTransaction_shouldEventuallyAppearInComplianceRecords_viaRealOutboxAndKafkaPipeline() {
    UUID accountA = createTestAccount("E2E-A-");
    UUID accountB = createTestAccount("E2E-B-");

    LedgerEntry debit = new LedgerEntry();
    debit.setAccountId(accountA);
    debit.setEntryType(EntryType.DEBIT);
    debit.setAmount(Money.of(new BigDecimal("250.00"), "VND"));

    LedgerEntry credit = new LedgerEntry();
    credit.setAccountId(accountB);
    credit.setEntryType(EntryType.CREDIT);
    credit.setAmount(Money.of(new BigDecimal("250.00"), "VND"));

    ledgerService.recordTransaction(List.of(debit, credit), "e2e-user");
    UUID transactionId = debit.getTransactionId();

    await().atMost(Duration.ofSeconds(20)).untilAsserted(() -> {
      List<ComplianceRecord> records = complianceRecordRepository.findByTransactionId(transactionId);
      assertEquals(2, records.size());
      assertTrue(records.stream().anyMatch(r ->
        r.getAccountId().equals(accountA) && r.getEntryType() == EntryType.DEBIT
          && r.getAmount().compareTo(new BigDecimal("250.00")) == 0
      ));
      assertTrue(records.stream().anyMatch(r ->
        r.getAccountId().equals(accountB) && r.getEntryType() == EntryType.CREDIT
      ));
      assertTrue(records.stream().allMatch(r -> r.getCreatedBy().equals("e2e-user")));
    });
  }

  @Test
  void reverseTransaction_shouldEventuallyAppearInComplianceRecords_withOppositeEntryTypes() {
    UUID accountA = createTestAccount("E2E-REV-A-");
    UUID accountB = createTestAccount("E2E-REV-B-");

    LedgerEntry debit = new LedgerEntry();
    debit.setAccountId(accountA);
    debit.setEntryType(EntryType.DEBIT);
    debit.setAmount(Money.of(new BigDecimal("300.00"), "VND"));

    LedgerEntry credit = new LedgerEntry();
    credit.setAccountId(accountB);
    credit.setEntryType(EntryType.CREDIT);
    credit.setAmount(Money.of(new BigDecimal("300.00"), "VND"));

    ledgerService.recordTransaction(List.of(debit, credit), "e2e-user");
    UUID originalTransactionId = debit.getTransactionId();

    // đợi transaction gốc được Audit consumer xử lý xong trước khi reverse,
    // để 2 event (Posted + Reversed) không lẫn lộn trạng thái chờ với nhau
    await().atMost(Duration.ofSeconds(20)).untilAsserted(() ->
      assertEquals(2, complianceRecordRepository.findByTransactionId(originalTransactionId).size())
    );

    ledgerService.reverseTransaction(originalTransactionId, "reversal-user");

    UUID reversalTransactionId = await().atMost(Duration.ofSeconds(20))
      .until(() -> transactionRepository.findByReversalOfTransactionId(originalTransactionId), Optional::isPresent)
      .map(Transaction::getId)
      .orElseThrow();

    await().atMost(Duration.ofSeconds(20)).untilAsserted(() -> {
      List<ComplianceRecord> reversalRecords = complianceRecordRepository.findByTransactionId(reversalTransactionId);
      assertEquals(2, reversalRecords.size());
      assertTrue(reversalRecords.stream().anyMatch(r ->
        r.getAccountId().equals(accountA) && r.getEntryType() == EntryType.CREDIT
          && r.getAmount().compareTo(new BigDecimal("300.00")) == 0
      ));
      assertTrue(reversalRecords.stream().anyMatch(r ->
        r.getAccountId().equals(accountB) && r.getEntryType() == EntryType.DEBIT
      ));
      assertTrue(reversalRecords.stream().allMatch(r -> r.getCreatedBy().equals("reversal-user")));
    });
  }
}
