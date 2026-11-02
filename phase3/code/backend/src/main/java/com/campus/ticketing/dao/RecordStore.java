package com.campus.ticketing.dao;

import java.util.*;

/** Typed persistence records and a closed predicate language; never accepts SQL from HTTP. */
public interface RecordStore {
  enum Op {
    EQ,
    NE,
    LT,
    LE,
    GT,
    GE,
    IN,
    NULL,
    NOT_NULL,
    AND,
    OR
  }

  record Filter(String field, Op op, Object value, List<Filter> children) {
    public static Filter eq(String f, Object v) {
      return v == null ? nil(f) : new Filter(f, Op.EQ, v, List.of());
    }

    public static Filter ne(String f, Object v) {
      return new Filter(f, Op.NE, v, List.of());
    }

    public static Filter lt(String f, Object v) {
      return new Filter(f, Op.LT, v, List.of());
    }

    public static Filter le(String f, Object v) {
      return new Filter(f, Op.LE, v, List.of());
    }

    public static Filter gt(String f, Object v) {
      return new Filter(f, Op.GT, v, List.of());
    }

    public static Filter ge(String f, Object v) {
      return new Filter(f, Op.GE, v, List.of());
    }

    public static Filter nil(String f) {
      return new Filter(f, Op.NULL, null, List.of());
    }

    public static Filter notNull(String f) {
      return new Filter(f, Op.NOT_NULL, null, List.of());
    }

    public static Filter in(String f, Collection<?> v) {
      return new Filter(f, Op.IN, List.copyOf(v), List.of());
    }

    public static Filter and(Filter... f) {
      return new Filter(null, Op.AND, null, Arrays.stream(f).filter(Objects::nonNull).toList());
    }

    public static Filter or(Filter... f) {
      return new Filter(null, Op.OR, null, Arrays.stream(f).filter(Objects::nonNull).toList());
    }
  }

  record Sort(String field, boolean descending) {
    public static Sort asc(String f) {
      return new Sort(f, false);
    }

    public static Sort desc(String f) {
      return new Sort(f, true);
    }
  }

  <T> List<T> select(
      Class<T> type, Filter filter, List<Sort> order, int limit, long offset, boolean lock);

  <T> long count(Class<T> type, Filter filter);

  <T> void insert(T entity);

  <T> void save(T entity);

  <T> void delete(Class<T> type, Filter filter);

  default void ensurePublicationDay(java.time.LocalDate day) {
    if (one(com.campus.ticketing.entity.PublicationQuotaEntity.class, Filter.eq("day", day), true)
        .isEmpty())
      insert(new com.campus.ticketing.entity.PublicationQuotaEntity(UUID.randomUUID(), day, 0, 0L));
  }

  default <T> Optional<T> one(Class<T> type, Filter filter, boolean lock) {
    return select(type, filter, List.of(), 1, 0, lock).stream().findFirst();
  }

  default <T> Optional<T> get(Class<T> type, UUID id, boolean lock) {
    return one(type, Filter.eq("id", id), lock);
  }
}
