package com.campus.ticketing.common;

import java.lang.reflect.*;
import java.util.*;

/**
 * Internal immutable-record construction/copy. Names are checked against actual Java components.
 */
public final class EntityRecords {
  private EntityRecords() {}

  public static <T> T create(Class<T> type, Object... pairs) {
    return construct(type, null, pairs);
  }

  @SuppressWarnings("unchecked")
  public static <T> T copy(T entity, Object... pairs) {
    return construct((Class<T>) entity.getClass(), entity, pairs);
  }

  private static <T> T construct(Class<T> type, Object source, Object... pairs) {
    if (pairs.length % 2 != 0) throw new IllegalArgumentException("Field/value pairs required");
    Map<String, Object> changes = new HashMap<>();
    Set<String> fields = new HashSet<>();
    for (var f : type.getRecordComponents()) fields.add(f.getName());
    for (int i = 0; i < pairs.length; i += 2) {
      String f = (String) pairs[i];
      if (!fields.contains(f)) throw new IllegalArgumentException("Unknown record field " + f);
      if (changes.containsKey(f)) throw new IllegalArgumentException("Duplicate field " + f);
      changes.put(f, pairs[i + 1]);
    }
    try {
      var components = type.getRecordComponents();
      Object[] values = new Object[components.length];
      for (int i = 0; i < components.length; i++) {
        var f = components[i];
        values[i] =
            changes.containsKey(f.getName())
                ? changes.get(f.getName())
                : source == null ? null : f.getAccessor().invoke(source);
      }
      return type.getDeclaredConstructor(
              Arrays.stream(components).map(RecordComponent::getType).toArray(Class<?>[]::new))
          .newInstance(values);
    } catch (ReflectiveOperationException e) {
      throw new IllegalArgumentException("Invalid record values for " + type.getSimpleName(), e);
    }
  }
}
