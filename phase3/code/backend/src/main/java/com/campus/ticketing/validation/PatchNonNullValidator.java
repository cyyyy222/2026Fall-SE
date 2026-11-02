package com.campus.ticketing.validation;

import com.campus.ticketing.common.PatchField;
import jakarta.validation.*;

public class PatchNonNullValidator implements ConstraintValidator<PatchNonNull, PatchField<?>> {
  @Override
  public boolean isValid(PatchField<?> f, ConstraintValidatorContext c) {
    return f == null || !f.present() || f.value() != null;
  }
}
