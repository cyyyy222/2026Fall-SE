package com.campus.ticketing.dao.jdbc;

import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/** Shared prepared-statement access. No business SQL or connection opens in offline mode. */
public class JdbcAccess {
  private final NamedParameterJdbcTemplate jdbc;

  public JdbcAccess(NamedParameterJdbcTemplate jdbc) {
    this.jdbc = jdbc;
  }

  public NamedParameterJdbcTemplate template() {
    return jdbc;
  }

  public void requireWriteTransaction() {
    if (!TransactionSynchronizationManager.isActualTransactionActive()
        || TransactionSynchronizationManager.isCurrentTransactionReadOnly())
      throw new IllegalStateException("Locking DAO operation requires a Service write transaction");
  }
}
