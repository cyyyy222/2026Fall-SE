package com.campus.ticketing.common;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.*;
import com.fasterxml.jackson.databind.deser.ContextualDeserializer;
import java.io.IOException;

public class PatchFieldDeserializer extends JsonDeserializer<PatchField<?>>
    implements ContextualDeserializer {
  private final JavaType valueType;

  public PatchFieldDeserializer() {
    this.valueType = null;
  }

  private PatchFieldDeserializer(JavaType valueType) {
    this.valueType = valueType;
  }

  @Override
  public JsonDeserializer<?> createContextual(
      DeserializationContext context, BeanProperty property) {
    JavaType t = context.getContextualType();
    if (t == null && property != null) t = property.getType();
    return new PatchFieldDeserializer(
        t == null ? context.constructType(Object.class) : t.containedTypeOrUnknown(0));
  }

  @Override
  public PatchField<?> deserialize(JsonParser p, DeserializationContext context)
      throws IOException {
    return PatchField.of(context.readValue(p, valueType));
  }

  @Override
  public PatchField<?> getNullValue(DeserializationContext c) {
    return PatchField.of(null);
  }

  @Override
  public PatchField<?> getAbsentValue(DeserializationContext c) {
    return PatchField.absent();
  }
}
