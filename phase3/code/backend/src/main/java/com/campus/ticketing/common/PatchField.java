package com.campus.ticketing.common;

import com.fasterxml.jackson.databind.annotation.JsonDeserialize;

@JsonDeserialize(using = PatchFieldDeserializer.class)
public record PatchField<T>(boolean present, T value) {
  public static <T> PatchField<T> absent() {
    return new PatchField<>(false, null);
  }

  public static <T> PatchField<T> of(T value) {
    return new PatchField<>(true, value);
  }
}
