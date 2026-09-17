package com.banking.core_banking_system.account;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.math.BigDecimal;
import java.time.Duration;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AccountBalanceCacheTest {

  private static final long TTL_SECONDS = 3600L;

  @Mock
  private StringRedisTemplate redisTemplate;

  @Mock
  private ValueOperations<String, String> valueOperations;

  private AccountBalanceCache cache;

  @BeforeEach
  void setUp() {
    cache = new AccountBalanceCache(redisTemplate, TTL_SECONDS);
  }

  @Test
  void get_shouldReturnEmpty_whenKeyMissingInRedis() {
    UUID accountId = UUID.randomUUID();
    when(redisTemplate.opsForValue()).thenReturn(valueOperations);
    when(valueOperations.get("balance:" + accountId)).thenReturn(null);

    assertTrue(cache.get(accountId).isEmpty());
  }

  @Test
  void get_shouldReturnParsedAmount_whenKeyPresentInRedis() {
    UUID accountId = UUID.randomUUID();
    when(redisTemplate.opsForValue()).thenReturn(valueOperations);
    when(valueOperations.get("balance:" + accountId)).thenReturn("150.50");

    Optional<BigDecimal> result = cache.get(accountId);

    assertTrue(result.isPresent());
    assertEquals(0, new BigDecimal("150.50").compareTo(result.get()));
  }

  @Test
  void put_shouldSetValueWithConfiguredTtl_usingKeyPattern() {
    UUID accountId = UUID.randomUUID();
    when(redisTemplate.opsForValue()).thenReturn(valueOperations);

    cache.put(accountId, new BigDecimal("200.00"));

    verify(valueOperations).set("balance:" + accountId, "200.00", Duration.ofSeconds(TTL_SECONDS));
  }

  @Test
  void evict_shouldDeleteKey_usingKeyPattern() {
    UUID accountId = UUID.randomUUID();

    cache.evict(accountId);

    verify(redisTemplate).delete("balance:" + accountId);
  }
}
