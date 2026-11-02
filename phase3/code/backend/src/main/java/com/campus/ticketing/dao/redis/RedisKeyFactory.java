package com.campus.ticketing.dao.redis;

import java.util.regex.Pattern;

/** Key suffix must be HMAC-SHA256 of normalized dimensions; never raw account/IP/phone. */
public class RedisKeyFactory {
  private final String prefix;

  public RedisKeyFactory(String environment) {
    if (!Pattern.matches("[a-z0-9_-]{1,24}", environment))
      throw new IllegalArgumentException("Invalid environment");
    prefix = "ticketing:" + environment + ":";
  }

  public String authRate(String action, String dimension, String fingerprint) {
    if (!java.util.Set.of("login", "register", "recovery").contains(action)
        || !java.util.Set.of("client", "account", "pair").contains(dimension)
        || !fingerprint.matches("[0-9a-f]{64}"))
      throw new IllegalArgumentException("Invalid rate key dimensions");
    return prefix + "rate:" + action + ":" + dimension + ":" + fingerprint;
  }
}
