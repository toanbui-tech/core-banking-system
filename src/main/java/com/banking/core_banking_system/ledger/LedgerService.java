package com.banking.core_banking_system.ledger;

import com.banking.core_banking_system.outbox.OutboxEvent;
import com.banking.core_banking_system.outbox.OutboxEventRepository;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import tools.jackson.databind.ObjectMapper;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
public class LedgerService {

  private final TransactionRepository transactionRepository;
  private final OutboxEventRepository outboxEventRepository;
  private final ObjectMapper objectMapper;
  private final ApplicationEventPublisher applicationEventPublisher;

  public LedgerService(
    TransactionRepository transactionRepository,
    OutboxEventRepository outboxEventRepository,
    ObjectMapper objectMapper,
    ApplicationEventPublisher applicationEventPublisher
  ) {
    this.transactionRepository = transactionRepository;
    this.outboxEventRepository = outboxEventRepository;
    this.objectMapper = objectMapper;
    this.applicationEventPublisher = applicationEventPublisher;
  }

  @Transactional
  public void recordTransaction(List<LedgerEntry> entries, String createdBy) {
    Transaction transaction = Transaction.record(entries, createdBy);
    transactionRepository.save(transaction);
    publishDomainEvents(transaction);
  }

  @Transactional
  public void reverseTransaction(UUID transactionId, String reversedBy) {
    Transaction original = transactionRepository.findById(transactionId)
      .orElseThrow(() -> new IllegalArgumentException("Transaction không tồn tại: " + transactionId));

    Transaction reversal = original.reverse(reversedBy);
    transactionRepository.save(reversal);
    publishDomainEvents(reversal);
  }

  /**
   * Lưu OutboxEvent trong CÙNG transaction DB với Transaction/LedgerEntry,
   * để đảm bảo tính nguyên tử giữa ghi nghiệp vụ và ghi sự kiện (Outbox Pattern).
   * Đồng thời publish event này như Spring application event nội bộ (không qua Kafka) —
   * dùng cho các listener trong cùng JVM (VD: AccountBalanceCacheEvictionListener),
   * chỉ thực thi sau khi DB transaction commit thành công (@TransactionalEventListener AFTER_COMMIT).
   */
  private void publishDomainEvents(Transaction transaction) {
    for (Object event : transaction.pullDomainEvents()) {
      String eventType = event.getClass().getSimpleName();
      String payload = objectMapper.writeValueAsString(event);
      outboxEventRepository.save(OutboxEvent.create(transaction.getId(), eventType, payload));
      applicationEventPublisher.publishEvent(event);
    }
  }
}
