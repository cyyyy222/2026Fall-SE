package com.campus.ticketing.validation;

import jakarta.validation.*;
import java.time.*;public class BeijingTimeValidator implements ConstraintValidator<BeijingTime, OffsetDateTime> {
  private int granularity;

  @Override
  public void initialize(BeijingTime a) {
    granularity = a.granularity();
  }

  @Override
  public boolean isValid(OffsetDateTime t, ConstraintValidatorContext c) {
    return t == null
        || (t.getNano() == 0
            && (granularity == 0 || t.toLocalTime().toSecondOfDay() % granularity == 0));
  }
}
