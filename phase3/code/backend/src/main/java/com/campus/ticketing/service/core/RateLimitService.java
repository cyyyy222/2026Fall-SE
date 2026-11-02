package com.campus.ticketing.service.core;

import static com.campus.ticketing.service.core.BusinessSupport.*;

import com.campus.ticketing.common.*;
import com.campus.ticketing.dao.redis.*;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.*;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Service;

@Service
public class RateLimitService {
  private final ObjectProvider<RedisTransientDao> counters;
  private final RedisKeyFactory keys;
  private final byte[] secret;

  public RateLimitService(ObjectProvider<RedisTransientDao> counters, Environment env) {
    this.counters = counters;
    keys = new RedisKeyFactory(env.getProperty("app.environment", "local"));
    secret = env.getProperty("app.rate.hmac-secret", "").getBytes(StandardCharsets.UTF_8);
  }

  public static String fingerprint(byte[] secret, String action, String dimension, String value) {
    try {
      Mac mac = Mac.getInstance("HmacSHA256");
      mac.init(new SecretKeySpec(secret, "HmacSHA256"));
      String content =
          "v1:"
              + action.length()
              + ":"
              + action
              + ":"
              + dimension.length()
              + ":"
              + dimension
              + ":"
              + value.length()
              + ":"
              + value;
      return HexFormat.of().formatHex(mac.doFinal(content.getBytes(StandardCharsets.UTF_8)));
    } catch (Exception e) {
      throw new IllegalStateException("HMAC not available", e);
    }
  }

  public void check(String action, RequestContext context, String account) {
    RedisTransientDao dao = counters.getIfAvailable();
    if (dao == null || secret.length < 32)
      throw error(503, "SERVICE_UNAVAILABLE", "认证限流尚未配置Redis或至少32字节HMAC密钥");
    record Limit(String dimension, String value, int count, int seconds) {}
    String client = Objects.requireNonNullElse(context.clientAddress(), "unavailable"),
        normalized = normalize(account);
    List<Limit> limits =
        action.equals("login")
            ? List.of(
                new Limit("client", client, 60, 60),
                new Limit("account", normalized, 30, 60),
                new Limit("pair", client.length() + ":" + client + ":" + normalized, 10, 60))
            : List.of(new Limit("client", client, 5, 600));
    long retry = 0;
    try {
      for (var limit : limits) {
        var result =
            dao.countWindow(
                keys.authRate(
                    action,
                    limit.dimension(),
                    fingerprint(secret, action, limit.dimension(), limit.value())),
                Duration.ofSeconds(limit.seconds()));
        if (result.count() > limit.count())
          retry = Math.max(retry, (result.remainingMs() + 999) / 1000);
      }
    } catch (Exception e) {
      throw error(503, "SERVICE_UNAVAILABLE", "认证入口限流服务不可用");
    }
    if (retry > 0) throw new Limited(retry);
  }

  public static class Limited extends ApiException {
    public final long retryAfter;

    public Limited(long retryAfter) {
      super(429, "TOO_MANY_REQUESTS", "请求过于频繁，请稍后重试");
      this.retryAfter = retryAfter;
    }
  }
}
