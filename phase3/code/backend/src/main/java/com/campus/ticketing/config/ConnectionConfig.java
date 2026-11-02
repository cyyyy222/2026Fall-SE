package com.campus.ticketing.config;

import com.campus.ticketing.dao.jdbc.JdbcAccess;
import com.zaxxer.hikari.*;
import java.time.Duration;
import javax.sql.DataSource;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.*;
import org.springframework.core.env.Environment;
import org.springframework.data.redis.connection.RedisStandaloneConfiguration;
import org.springframework.data.redis.connection.lettuce.*;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.support.JdbcTransactionManager;
import org.springframework.transaction.annotation.EnableTransactionManagement;

@Configuration
@EnableTransactionManagement
@ConditionalOnProperty(name = "app.connections.enabled", havingValue = "true")
public class ConnectionConfig {
  @Bean(destroyMethod = "close")
  HikariDataSource dataSource(Environment env) {
    HikariConfig c = new HikariConfig();
    c.setJdbcUrl(env.getRequiredProperty("app.mysql.url"));
    c.setUsername(env.getRequiredProperty("app.mysql.username"));
    c.setPassword(env.getRequiredProperty("app.mysql.password"));
    c.setMaximumPoolSize(10);
    c.setConnectionTimeout(3000);
    c.setInitializationFailTimeout(-1);
    c.setTransactionIsolation("TRANSACTION_READ_COMMITTED");
    return new HikariDataSource(c);
  }

  @Bean
  NamedParameterJdbcTemplate jdbc(DataSource ds) {
    return new NamedParameterJdbcTemplate(ds);
  }

  @Bean
  JdbcAccess jdbcAccess(NamedParameterJdbcTemplate jdbc) {
    return new JdbcAccess(jdbc);
  }

  @Bean
  JdbcTransactionManager transactionManager(DataSource ds) {
    return new JdbcTransactionManager(ds);
  }

  @Bean(destroyMethod = "destroy")
  @ConditionalOnProperty(name = "app.redis.enabled", havingValue = "true", matchIfMissing = true)
  LettuceConnectionFactory redisConnectionFactory(Environment env) {
    RedisStandaloneConfiguration c =
        new RedisStandaloneConfiguration(
            env.getProperty("app.redis.host", "127.0.0.1"),
            env.getProperty("app.redis.port", Integer.class, 6379));
    String password = env.getProperty("app.redis.password", "");
    if (!password.isBlank()) c.setPassword(password);
    var client = LettuceClientConfiguration.builder().commandTimeout(Duration.ofSeconds(2)).build();
    return new LettuceConnectionFactory(c, client);
  }

  @Bean
  @ConditionalOnProperty(name = "app.redis.enabled", havingValue = "true", matchIfMissing = true)
  StringRedisTemplate stringRedisTemplate(LettuceConnectionFactory factory) {
    return new StringRedisTemplate(factory);
  }
}
