package com.campus.ticketing.service.impl;

import com.campus.ticketing.dto.views.Views;
import com.campus.ticketing.service.SystemStatusService;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;

@Service
public class SystemStatusServiceImpl implements SystemStatusService {
  private final ObjectProvider<NamedParameterJdbcTemplate> jdbc;
  private final ObjectProvider<StringRedisTemplate> redis;

  public SystemStatusServiceImpl(
      ObjectProvider<NamedParameterJdbcTemplate> jdbc, ObjectProvider<StringRedisTemplate> redis) {
    this.jdbc = jdbc;
    this.redis = redis;
  }

  public Views.Health health() {
    String mysql = "NOT_CONNECTED", cache = "NOT_CONNECTED";
    var j = jdbc.getIfAvailable();
    if (j != null) {
      try {
        j.queryForObject("SELECT 1", java.util.Map.of(), Integer.class);
        mysql = "UP";
      } catch (Exception e) {
        mysql = "DOWN";
      }
    }
    var r = redis.getIfAvailable();
    if (r != null) {
      try (var c = r.getConnectionFactory().getConnection()) {
        cache = "PONG".equals(c.ping()) ? "UP" : "DOWN";
      } catch (Exception e) {
        cache = "DOWN";
      }
    }
    return new Views.Health(
        "UP".equals(mysql) && "UP".equals(cache) ? "UP" : "NOT_READY",
        new Views.HealthComponents(mysql, cache));
  }
}
