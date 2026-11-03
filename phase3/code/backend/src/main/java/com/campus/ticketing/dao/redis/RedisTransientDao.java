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

  private static final DefaultRedisScript<Long> ISSUE_CODE =
      new DefaultRedisScript<>(
          "if not redis.call('SET',KEYS[2],'1','NX','EX',60) then return 0 end; "
              + "redis.call('HSET',KEYS[1],'id',ARGV[1],'hash',ARGV[2],'attempts',5); "
              + "redis.call('EXPIRE',KEYS[1],300); return 1",
          Long.class);

  private static final DefaultRedisScript<Long> VERIFY_CODE =
      new DefaultRedisScript<>(
          "if redis.call('HGET',KEYS[1],'id')~=ARGV[1] then return 0 end; if"
              + " redis.call('HGET',KEYS[1],'hash')~=ARGV[2] then local"
              + " n=redis.call('HINCRBY',KEYS[1],'attempts',-1); if n<=0 then"
              + " redis.call('DEL',KEYS[1]) end; return -1 end; if ARGV[3]=='1' then"
              + " redis.call('DEL',KEYS[1]) end; return 1",
          Long.class);

  public boolean issuePhoneCode(String key, String cooldownKey, String id, String hash) {
    return Long.valueOf(1).equals(redis.execute(ISSUE_CODE, List.of(key, cooldownKey), id, hash));
  }

  public long verifyPhoneCode(String key, String id, String hash, boolean consume) {
    Long result = redis.execute(VERIFY_CODE, List.of(key), id, hash, consume ? "1" : "0");
    if (result == null) throw new IllegalStateException("No phone code verification result");
    return result;
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
