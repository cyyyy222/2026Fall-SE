package com.campus.ticketing.dao.redis;

import java.time.Duration;
import java.util.List;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;

/** Atomic Redis window adapter used by authentication rate limits; enabled only when connected. */
@org.springframework.stereotype.Repository
@org.springframework.boot.autoconfigure.condition.ConditionalOnExpression(
    "${app.connections.enabled:false} && ${app.redis.enabled:true}")
public class RedisTransientDao {
  private static final DefaultRedisScript<Long> INCREMENT =
      new DefaultRedisScript<>(
          "local n=redis.call('INCR',KEYS[1]); if n==1 or redis.call('PTTL',KEYS[1])<0 then"
              + " redis.call('PEXPIRE',KEYS[1],ARGV[1]); end; return n",
          Long.class);
  private final StringRedisTemplate redis;

  public RedisTransientDao(StringRedisTemplate redis) {
    this.redis = redis;
  }

  public long incrementWindow(String key, Duration ttl) {
    if (ttl.isNegative() || ttl.isZero())
      throw new IllegalArgumentException("TTL must be positive");
    Long n = redis.execute(INCREMENT, List.of(key), Long.toString(ttl.toMillis()));
    if (n == null) throw new IllegalStateException("Redis returned no result");
    return n;
  }

  @SuppressWarnings("rawtypes")
  private static final DefaultRedisScript<List> WINDOW =
      new DefaultRedisScript<>(
          "local n=redis.call('INCR',KEYS[1]); local t=redis.call('PTTL',KEYS[1]); if n==1 or t<0"
              + " then redis.call('PEXPIRE',KEYS[1],ARGV[1]); t=tonumber(ARGV[1]); end; return"
              + " {n,t}",
          List.class);

  public record Counter(long count, long remainingMs) {}

  @SuppressWarnings("rawtypes")
  public Counter countWindow(String key, Duration ttl) {
    if (ttl.isNegative() || ttl.isZero())
      throw new IllegalArgumentException("TTL must be positive");
    List result = redis.execute(WINDOW, List.of(key), Long.toString(ttl.toMillis()));
    if (result == null || result.size() != 2)
      throw new IllegalStateException("Invalid Redis counter result");
    return new Counter(((Number) result.get(0)).longValue(), ((Number) result.get(1)).longValue());
  }
}
