package com.banking.core_banking_system.account;

import com.banking.core_banking_system.ledger.EntryType;
import com.banking.core_banking_system.ledger.LedgerEntry;
import com.banking.core_banking_system.ledger.LedgerService;
import com.banking.core_banking_system.shared.money.Money;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import java.math.BigDecimal;
import java.util.Currency;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Test thật với Redis qua Testcontainers (nhất quán với cách project dùng Testcontainers cho Kafka),
 * xác nhận cache-aside hoạt động đúng và cache bị xóa đúng sau khi có giao dịch mới —
 * không dựa vào giả định, verify trực tiếp trên Redis thật.
 */
@SpringBootTest(properties = "outbox.publisher.enabled=false")
@Testcontainers
class AccountBalanceCacheIntegrationTest {

  @Container
  static final GenericContainer<?> REDIS = new GenericContainer<>(DockerImageName.parse("redis:7-alpine"))
    .withExposedPorts(6379);

  @DynamicPropertySource
  static void redisProperties(DynamicPropertyRegistry registry) {
    registry.add("spring.data.redis.host", REDIS::getHost);
    registry.add("spring.data.redis.port", () -> REDIS.getMappedPort(6379));
  }

  @Autowired
  private AccountService accountService;

  @Autowired
  private AccountRepository accountRepository;

  @Autowired
  private LedgerService ledgerService;

  @Autowired
  private StringRedisTemplate redisTemplate;

  private UUID createTestAccount(String prefix) {
    Account account = new Account();
    account.setAccountNumber(prefix + UUID.randomUUID().toString().substring(0, 8));
    account.setAccountType("CASH");
    account.setCurrency(Currency.getInstance("VND"));
    account.setStatus("ACTIVE");
    return accountRepository.save(account).getId();
  }

  private LedgerEntry entry(UUID accountId, EntryType type, String amount) {
    LedgerEntry entry = new LedgerEntry();
    entry.setAccountId(accountId);
    entry.setEntryType(type);
    entry.setAmount(Money.of(new BigDecimal(amount), "VND"));
    return entry;
  }

  @Test
  void getBalance_shouldPopulateRedis_onFirstCallAfterCacheMiss() {
    UUID accountId = createTestAccount("CACHE-A-");
    UUID counterparty = createTestAccount("CACHE-B-");

    ledgerService.recordTransaction(
      List.of(entry(accountId, EntryType.CREDIT, "100.00"), entry(counterparty, EntryType.DEBIT, "100.00")),
      "test-user"
    );

    Money balance = accountService.getBalance(accountId);

    assertEquals(Money.of(new BigDecimal("100.00"), "VND"), balance);
    assertEquals("100.00", redisTemplate.opsForValue().get("balance:" + accountId));
  }

  @Test
  void getBalance_shouldServeStaleCachedValue_untilTransactionInvalidatesIt() {
    UUID accountId = createTestAccount("CACHE-C-");
    UUID counterparty = createTestAccount("CACHE-D-");

    ledgerService.recordTransaction(
      List.of(entry(accountId, EntryType.CREDIT, "100.00"), entry(counterparty, EntryType.DEBIT, "100.00")),
      "test-user"
    );

    // Lần gọi đầu: cache miss -> populate Redis với 100.00
    assertEquals(Money.of(new BigDecimal("100.00"), "VND"), accountService.getBalance(accountId));

    // Sửa thẳng dữ liệu trong Redis để mô phỏng "cache đang giữ giá trị cũ" một cách rõ ràng,
    // xác nhận getBalance() thật sự đọc từ cache (không âm thầm bỏ qua) khi cache còn tồn tại
    redisTemplate.opsForValue().set("balance:" + accountId, "999.99");
    assertEquals(Money.of(new BigDecimal("999.99"), "VND"), accountService.getBalance(accountId));
  }

  @Test
  void getBalance_shouldNotReturnStaleBalance_afterNewTransactionRecorded() {
    UUID accountId = createTestAccount("CACHE-E-");
    UUID counterparty = createTestAccount("CACHE-F-");

    ledgerService.recordTransaction(
      List.of(entry(accountId, EntryType.CREDIT, "100.00"), entry(counterparty, EntryType.DEBIT, "100.00")),
      "test-user"
    );
    assertEquals(Money.of(new BigDecimal("100.00"), "VND"), accountService.getBalance(accountId));

    // Giao dịch mới trên cùng account -> cache PHẢI bị evict sau commit (AFTER_COMMIT, đồng bộ,
    // xảy ra trước khi recordTransaction() trả về), nên không cần chờ/poll ở đây
    ledgerService.recordTransaction(
      List.of(entry(accountId, EntryType.CREDIT, "50.00"), entry(counterparty, EntryType.DEBIT, "50.00")),
      "test-user"
    );

    assertEquals(Money.of(new BigDecimal("150.00"), "VND"), accountService.getBalance(accountId));
  }

  @Test
  void getBalance_shouldNotReturnStaleBalance_afterWithdraw() {
    UUID accountId = createTestAccount("CACHE-G-");
    UUID counterparty = createTestAccount("CACHE-H-");

    ledgerService.recordTransaction(
      List.of(entry(accountId, EntryType.CREDIT, "200.00"), entry(counterparty, EntryType.DEBIT, "200.00")),
      "test-user"
    );
    assertEquals(Money.of(new BigDecimal("200.00"), "VND"), accountService.getBalance(accountId));

    accountService.withdraw(accountId, counterparty, Money.of(new BigDecimal("80.00"), "VND"), "test-user");

    assertEquals(Money.of(new BigDecimal("120.00"), "VND"), accountService.getBalance(accountId));
  }
}
