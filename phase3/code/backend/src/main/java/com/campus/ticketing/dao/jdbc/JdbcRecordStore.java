package com.campus.ticketing.dao.jdbc;

import com.campus.ticketing.dao.RecordStore;
import java.lang.reflect.*;
import java.sql.*;
import java.time.LocalDate;
import java.util.*;
import java.util.stream.Collectors;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/** All identifiers come from the generated, fixed schema registry; values are bound parameters. */
@Repository
@ConditionalOnProperty(name = "app.connections.enabled", havingValue = "true")
public class JdbcRecordStore implements RecordStore {
  private final NamedParameterJdbcTemplate jdbc;

  public JdbcRecordStore(NamedParameterJdbcTemplate jdbc) {
    this.jdbc = jdbc;
  }

  private static String table(Class<?> type) {
    String t = SchemaRegistry.TABLES.get(type);
    if (t == null) throw new IllegalArgumentException("Unregistered entity");
    return "`" + t + "`";
  }

  private static String column(Class<?> type, String property) {
    if (Arrays.stream(type.getRecordComponents()).noneMatch(f -> f.getName().equals(property)))
      throw new IllegalArgumentException("Unknown entity property: " + property);
    return "`" + property.replaceAll("([a-z0-9])([A-Z])", "$1_$2").toLowerCase(Locale.ROOT) + "`";
  }

  private static Object sqlValue(Object value) {
    return value instanceof UUID || value instanceof Enum<?> ? value.toString() : value;
  }

  private static void writeTransaction() {
    if (!TransactionSynchronizationManager.isActualTransactionActive()
        || TransactionSynchronizationManager.isCurrentTransactionReadOnly())
      throw new IllegalStateException("DAO write/lock requires Service transaction");
  }

  private String where(Class<?> type, Filter f, Map<String, Object> p) {
    if (f == null) return "1=1";
    if (f.op() == Op.AND || f.op() == Op.OR) {
      if (f.children().isEmpty()) return f.op() == Op.AND ? "1=1" : "1=0";
      return f.children().stream()
          .map(c -> where(type, c, p))
          .collect(Collectors.joining(f.op() == Op.AND ? " AND " : " OR ", "(", ")"));
    }
    String c = column(type, f.field());
    if (f.op() == Op.NULL || f.op() == Op.NOT_NULL)
      return c + (f.op() == Op.NULL ? " IS NULL" : " IS NOT NULL");
    String key = "p" + p.size();
    if (f.op() == Op.IN) {
      var values = (Collection<?>) f.value();
      if (values.isEmpty()) return "1=0";
      p.put(key, values.stream().map(JdbcRecordStore::sqlValue).toList());
      return c + " IN (:" + key + ")";
    }
    p.put(key, sqlValue(f.value()));
    return c
        + switch (f.op()) {
          case EQ -> " = ";
          case NE -> " <> ";
          case LT -> " < ";
          case LE -> " <= ";
          case GT -> " > ";
          case GE -> " >= ";
          default -> throw new IllegalArgumentException("Operator");
        }
        + ":"
        + key;
  }

  @Override
  public <T> List<T> select(
      Class<T> type, Filter filter, List<Sort> order, int limit, long offset, boolean lock) {
    if (limit < 1 || offset < 0) throw new IllegalArgumentException("Invalid page");
    if (lock) writeTransaction();
    Map<String, Object> p = new HashMap<>();
    String w = where(type, filter, p);
    String sort =
        order.isEmpty()
            ? ""
            : order.stream()
                .map(o -> column(type, o.field()) + (o.descending() ? " DESC" : " ASC"))
                .collect(Collectors.joining(",", " ORDER BY ", ""));
    p.put("limit", limit);
    p.put("offset", offset);
    String columns =
        Arrays.stream(type.getRecordComponents())
            .map(f -> column(type, f.getName()))
            .collect(Collectors.joining(","));
    return jdbc.query(
        "SELECT "
            + columns
            + " FROM "
            + table(type)
            + " WHERE "
            + w
            + sort
            + " LIMIT :limit OFFSET :offset"
            + (lock ? " FOR UPDATE" : ""),
        p,
        (rs, n) -> map(type, rs));
  }

  @Override
  public <T> long count(Class<T> type, Filter filter) {
    Map<String, Object> p = new HashMap<>();
    return Objects.requireNonNull(
        jdbc.queryForObject(
            "SELECT COUNT(*) FROM " + table(type) + " WHERE " + where(type, filter, p),
            p,
            Long.class));
  }

  @Override
  public <T> void insert(T entity) {
    writeTransaction();
    Class<?> type = entity.getClass();
    Map<String, Object> p = parameters(entity);
    String cols = p.keySet().stream().map(f -> column(type, f)).collect(Collectors.joining(","));
    String vals = p.keySet().stream().map(f -> ":" + f).collect(Collectors.joining(","));
    jdbc.update("INSERT INTO " + table(type) + " (" + cols + ") VALUES (" + vals + ")", p);
  }

  @Override
  public <T> void save(T entity) {
    writeTransaction();
    Class<?> type = entity.getClass();
    Map<String, Object> p = parameters(entity);
    String sets =
        p.keySet().stream()
            .filter(f -> !f.equals("id"))
            .map(f -> column(type, f) + "=:" + f)
            .collect(Collectors.joining(","));
    if (jdbc.update("UPDATE " + table(type) + " SET " + sets + " WHERE id=:id", p) != 1)
      throw new IllegalStateException("Expected one persisted row");
  }

  @Override
  public <T> void delete(Class<T> type, Filter filter) {
    writeTransaction();
    if (filter == null) throw new IllegalArgumentException("Unbounded delete");
    Map<String, Object> p = new HashMap<>();
    jdbc.update("DELETE FROM " + table(type) + " WHERE " + where(type, filter, p), p);
  }

  @Override
  public void ensurePublicationDay(LocalDate day) {
    writeTransaction();
    jdbc.update(
        "INSERT INTO publication_quotas (id,day,published_count,version) VALUES (:id,:day,0,0) ON"
            + " DUPLICATE KEY UPDATE day=day",
        Map.of("id", UUID.randomUUID().toString(), "day", day));
  }

  private static Map<String, Object> parameters(Object entity) {
    var p = new LinkedHashMap<String, Object>();
    try {
      for (var f : entity.getClass().getRecordComponents())
        p.put(f.getName(), sqlValue(f.getAccessor().invoke(entity)));
      return p;
    } catch (ReflectiveOperationException e) {
      throw new IllegalStateException(e);
    }
  }

  public static <T> T map(Class<T> type, ResultSet rs) throws SQLException {
    var fields = type.getRecordComponents();
    Object[] values = new Object[fields.length];
    for (int i = 0; i < fields.length; i++) {
      String col =
          fields[i].getName().replaceAll("([a-z0-9])([A-Z])", "$1_$2").toLowerCase(Locale.ROOT);
      Object value = rs.getObject(col);
      Class<?> t = fields[i].getType();
      if (value != null) {
        if (t == UUID.class) value = UUID.fromString(value.toString());
        else if (t == Long.class) value = ((Number) value).longValue();
        else if (t == Integer.class) value = ((Number) value).intValue();
        else if (t == Boolean.class)
          value = value instanceof Boolean ? value : ((Number) value).intValue() != 0;
        else if (t == LocalDate.class) value = rs.getDate(col).toLocalDate();
        else if (t == String.class) value = value.toString();
      }
      values[i] = value;
    }
    try {
      return type.getDeclaredConstructor(
              Arrays.stream(fields).map(RecordComponent::getType).toArray(Class<?>[]::new))
          .newInstance(values);
    } catch (ReflectiveOperationException e) {
      throw new SQLException("Cannot map " + type.getSimpleName(), e);
    }
  }
}
