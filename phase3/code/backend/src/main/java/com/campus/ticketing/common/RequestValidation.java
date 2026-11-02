package com.campus.ticketing.common;

import jakarta.validation.ConstraintViolationException;
import jakarta.validation.Validator;
import org.springframework.stereotype.Component;

@Component
public class RequestValidation {
  private final Validator validator;

  public RequestValidation(Validator validator) {
    this.validator = validator;
  }

  public void check(Object value) {
    var failures = validator.validate(value);
    if (!failures.isEmpty()) throw new ConstraintViolationException(failures);
  }
}
