package com.campus.ticketing;

import com.campus.ticketing.dao.RecordStore;
import java.lang.reflect.*;
import java.util.*;

/** Test fixture only. Does NOT emulate MySQL locks, constraints, isolation or durability. */
final class TestRecordStore implements RecordStore {
  private final Map<Class<?>, Map<UUID, Object>> rows = new HashMap<>();

  private Object value(Object row, String field) {
    try {
      return row.getClass().getMethod(field).invoke(row);
    } catch (ReflectiveOperationException e) {
      throw new AssertionError("Unknown fixture field " + field, e);
    }
  }

  private UUID id(Object row) {
    return (UUID) value(row, "id");
  }

  @SuppressWarnings({"rawtypes", "unchecked"})
  private int compare(Object left, Object right) {
    if (left == null) return right == null ? 0 : -1;
    if (right == null) return 1;
    if (left instanceof UUID) return left.toString().compareTo(right.toString());
    if (left instanceof Number && right instanceof Number)
      return Long.compare(((Number) left).longValue(), ((Number) right).longValue());
    return ((Comparable) left).compareTo(right);
  }

  private boolean matches(Object row, Filter f) {
    if (f == null) return true;
    if (f.op() == Op.AND) return f.children().stream().allMatch(c -> matches(row, c));
    if (f.op() == Op.OR) return f.children().stream().anyMatch(c -> matches(row, c));
    Object v = value(row, f.field());
    return switch (f.op()) {
      case NULL -> v == null;
      case NOT_NULL -> v != null;
      case EQ -> Objects.equals(v, f.value());
      case NE -> v != null && !Objects.equals(v, f.value());
      case LT -> v != null && compare(v, f.value()) < 0;
      case LE -> v != null && compare(v, f.value()) <= 0;
      case GT -> v != null && compare(v, f.value()) > 0;
      case GE -> v != null && compare(v, f.value()) >= 0;
      case IN -> ((Collection<?>) f.value()).contains(v);
      default -> throw new AssertionError();
    };
  }

  @Override
  public <T> List<T> select(
      Class<T> type, Filter filter, List<Sort> order, int limit, long offset, boolean lock) {
    var all =
        rows.getOrDefault(type, Map.of()).values().stream()
            .map(type::cast)
            .filter(v -> matches(v, filter));
    Comparator<T> comparator = (a, b) -> 0;
    for (var s : order) {
      Comparator<T> next = (a, b) -> compare(value(a, s.field()), value(b, s.field()));
      if (s.descending()) next = next.reversed();
      comparator = comparator.thenComparing(next);
    }
    return all.sorted(comparator).skip(offset).limit(limit).toList();
  }

  @Override
  public <T> long count(Class<T> type, Filter filter) {
    return select(type, filter, List.of(), Integer.MAX_VALUE, 0, false).size();
  }

  @Override
  public <T> void insert(T entity) {
    var table = rows.computeIfAbsent(entity.getClass(), k -> new LinkedHashMap<>());
    if (table.putIfAbsent(id(entity), entity) != null)
      throw new AssertionError("Duplicate fixture ID");
  }

  @Override
  public <T> void save(T entity) {
    var table = rows.get(entity.getClass());
    if (table == null || !table.containsKey(id(entity)))
      throw new AssertionError("Missing fixture row");
    table.put(id(entity), entity);
  }

  @Override
  public <T> void delete(Class<T> type, Filter filter) {
    rows.getOrDefault(type, new HashMap<>()).values().removeIf(row -> matches(row, filter));
  }
}
