package com.campus.ticketing.validation;

import com.campus.ticketing.common.PatchField;
import jakarta.validation.valueextraction.*;

public class PatchValueExtractor implements ValueExtractor<PatchField<@ExtractedValue ?>> {
  @Override
  public void extractValues(PatchField<?> f, ValueReceiver receiver) {
    if (f != null && f.present()) receiver.value(null, f.value());
  }
}
