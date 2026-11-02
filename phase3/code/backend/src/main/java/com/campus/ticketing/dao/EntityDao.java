package com.campus.ticketing.dao;

import java.util.Optional;
import java.util.UUID;

/** Preparation port only. Locking call must be used inside a Service write transaction. */
public interface EntityDao<E> {
  Optional<E> findById(UUID id);

  Optional<E> lockById(UUID id);

  void insert(E entity);
}
