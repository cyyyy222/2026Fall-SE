package com.campus.ticketing;

import static com.campus.ticketing.dao.RecordStore.Filter.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.campus.ticketing.common.*;
import com.campus.ticketing.dao.RecordStore;
import com.campus.ticketing.dao.jdbc.JdbcRecordStore;
import com.campus.ticketing.dao.redis.*;
import com.campus.ticketing.entity.*;
import com.campus.ticketing.service.core.*;
import java.sql.ResultSet;
import java.time.Duration;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.mock.env.MockEnvironment;

class JdbcAndRedisAdapterTest {
  @SuppressWarnings({"unchecked", "rawtypes"})
  @Test
  void sqlBindsValuesAndRejectsUnknownIdentifiers() {
    var jdbc = mock(NamedParameterJdbcTemplate.class);
    doReturn(List.of()).when(jdbc).query(anyString(), any(Map.class), any(RowMapper.class));
    var store = new JdbcRecordStore(jdbc);
    String value = "x' OR 1=1 --";
    store.select(
        CategoryEntity.class,
        eq("nameNormalized", value),
        List.of(RecordStore.Sort.asc("nameNormalized")),
        20,
        0,
        false);
    var sql = ArgumentCaptor.forClass(String.class);
    var params = ArgumentCaptor.forClass(Map.class);
    verify(jdbc).query(sql.capture(), params.capture(), any(RowMapper.class));
    assertThat(sql.getValue()).doesNotContain(value).contains("name_normalized` = :p0");
    assertThat(params.getValue().get("p0")).isEqualTo(value);
    assertThatThrownBy(
            () ->
                store.select(
                    CategoryEntity.class, eq("name` OR 1=1", value), List.of(), 1, 0, false))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void lockingDaoRequiresAServiceWriteTransaction() {
    var store = new JdbcRecordStore(mock(NamedParameterJdbcTemplate.class));
    assertThatThrownBy(() -> store.select(CategoryEntity.class, null, List.of(), 1, 0, true))
        .isInstanceOf(IllegalStateException.class);
  }

  @Test
  void jdbcMapperPreservesUuidBooleanNullableAndIntegerTypes() throws Exception {
    var rs = mock(ResultSet.class);
    UUID id = UUID.randomUUID();
    when(rs.getObject("id")).thenReturn(id.toString());
    when(rs.getObject("name")).thenReturn("类别");
    when(rs.getObject("name_normalized")).thenReturn("类别");
    when(rs.getObject("enabled")).thenReturn(1);
    when(rs.getObject("version")).thenReturn(2L);
    when(rs.getObject("created_at_ms")).thenReturn(1000L);
    when(rs.getObject("updated_at_ms")).thenReturn(1000L);
    var row = JdbcRecordStore.map(CategoryEntity.class, rs);
    assertThat(row.id()).isEqualTo(id);
    assertThat(row.enabled()).isTrue();
    assertThat(row.version()).isEqualTo(2L);
  }

  @SuppressWarnings("unchecked")
  @Test
  void rateLimiterUsesAllThreeDimensionsAndReturnsRemainingWindow() {
    var provider = mock(ObjectProvider.class);
    var dao = mock(RedisTransientDao.class);
    when(provider.getIfAvailable()).thenReturn(dao);
    when(dao.countWindow(anyString(), any())).thenReturn(new RedisTransientDao.Counter(11, 1501));
    var env =
        new MockEnvironment()
            .withProperty("app.rate.hmac-secret", "s".repeat(32))
            .withProperty("app.environment", "test");
    var limiter = new RateLimitService(provider, env);
    assertThatThrownBy(
            () ->
                limiter.check(
                    "login", new RequestContext(null, null, "test", "127.0.0.1"), "Alice"))
        .isInstanceOfSatisfying(
            RateLimitService.Limited.class, e -> assertThat(e.retryAfter).isEqualTo(2));
    verify(dao, times(3))
        .countWindow(startsWith("ticketing:test:rate:login:"), eq(Duration.ofSeconds(60)));
  }

  @SuppressWarnings("unchecked")
  @Test
  void noRedisOrSecretFailsClosed() {
    var provider = mock(ObjectProvider.class);
    var limiter = new RateLimitService(provider, new MockEnvironment());
    assertThatThrownBy(
            () ->
                limiter.check(
                    "register", new RequestContext(null, null, "test", "127.0.0.1"), "Alice"))
        .isInstanceOfSatisfying(ApiException.class, e -> assertThat(e.status()).isEqualTo(503));
  }
}
