package com.campus.ticketing.validation;

import jakarta.validation.*;

public class CodePointLengthValidator implements ConstraintValidator<CodePointLength, String> {
  private int min, max;

  @Override
  public void initialize(CodePointLength a) {
    min = a.min();
    max = a.max();
  }

  @Override
  public boolean isValid(String value, ConstraintValidatorContext c) {
    if (value == null) return true;
    int n = value.strip().codePointCount(0, value.strip().length());
    return n >= min && n <= max;
  }
}
