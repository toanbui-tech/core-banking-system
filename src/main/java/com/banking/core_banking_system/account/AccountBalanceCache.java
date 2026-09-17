package com.banking.core_banking_system.account;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.Duration;
import java.util.Optional;
import java.util.UUID;

/**
 * Cache-aside cho balance của account. Chỉ lưu số tiền (BigDecimal dạng chuỗi) —
 * currency luôn đọc từ Account (không đổi theo thời gian), không cần lưu trong cache.
 */
@Component
public class AccountBalanceCache {

  private static final String KEY_PREFIX = "balance:";

  private final StringRedisTemplate redisTemplate;
  private final Duration ttl;

  public AccountBalanceCache(
    StringRedisTemplate redisTemplate,
    @Value("${app.cache.balance.ttl-seconds}") long ttlSeconds
  ) {
    this.redisTemplate = redisTemplate;
    this.ttl = Duration.ofSeconds(ttlSeconds);
  }

  public Optional<BigDecimal> get(UUID accountId) {
    return Optional.ofNullable(redisTemplate.opsForValue().get(key(accountId))).map(BigDecimal::new);
  }

  public void put(UUID accountId, BigDecimal amount) {
    redisTemplate.opsForValue().set(key(accountId), amount.toPlainString(), ttl);
  }

  public void evict(UUID accountId) {
    redisTemplate.delete(key(accountId));
  }

  private String key(UUID accountId) {
    return KEY_PREFIX + accountId;
  }
}
