package com.banking.core_banking_system.account;

import com.banking.core_banking_system.ledger.event.LedgerEntrySnapshot;
import com.banking.core_banking_system.ledger.event.TransactionPostedEvent;
import com.banking.core_banking_system.ledger.event.TransactionReversedEvent;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import java.util.List;

/**
 * Xóa cache balance cho mọi account liên quan sau khi Transaction.record()/reverse() commit
 * thành công. Chạy ở AFTER_COMMIT (không phải trong transaction) để tránh race condition:
 * nếu evict trước commit, 1 request đọc đồng thời có thể query DB (thấy balance CŨ vì chưa
 * commit) và ghi đè lại vào cache ngay sau đó — balance sai sẽ kẹt lại tới hết TTL.
 */
@Component
public class AccountBalanceCacheEvictionListener {

  private final AccountBalanceCache balanceCache;

  public AccountBalanceCacheEvictionListener(AccountBalanceCache balanceCache) {
    this.balanceCache = balanceCache;
  }

  @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
  public void onTransactionPosted(TransactionPostedEvent event) {
    evictAll(event.entries());
  }

  @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
  public void onTransactionReversed(TransactionReversedEvent event) {
    evictAll(event.entries());
  }

  private void evictAll(List<LedgerEntrySnapshot> entries) {
    entries.stream()
      .map(LedgerEntrySnapshot::accountId)
      .distinct()
      .forEach(balanceCache::evict);
  }
}
