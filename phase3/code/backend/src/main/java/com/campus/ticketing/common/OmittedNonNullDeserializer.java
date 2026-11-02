package com.campus.ticketing.common;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.*;
import com.fasterxml.jackson.databind.deser.ContextualDeserializer;
import com.fasterxml.jackson.databind.exc.InvalidNullException;
import java.io.IOException;

/** For record fields that may be omitted but must not be explicitly null. */
public class OmittedNonNullDeserializer extends JsonDeserializer<Object>
    implements ContextualDeserializer {
  private final JavaType type;
  private final PropertyName name;

  public OmittedNonNullDeserializer() {
    type = null;
    name = null;
  }

  private OmittedNonNullDeserializer(JavaType type, PropertyName name) {
    this.type = type;
    this.name = name;
  }

  @Override
  public JsonDeserializer<?> createContextual(DeserializationContext c, BeanProperty p) {
    return new OmittedNonNullDeserializer(p.getType(), p.getFullName());
  }

  @Override
  public Object deserialize(JsonParser p, DeserializationContext c) throws IOException {
    return c.readValue(p, type);
  }

  @Override
  public Object getNullValue(DeserializationContext c) throws JsonMappingException {
    throw InvalidNullException.from(c, name, type);
  }

  @Override
  public Object getAbsentValue(DeserializationContext c) {
    return null;
  }
}
