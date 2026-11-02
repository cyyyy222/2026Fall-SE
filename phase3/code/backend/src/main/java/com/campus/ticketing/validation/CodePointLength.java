package com.campus.ticketing.validation;

import jakarta.validation.*;
import java.lang.annotation.*;

@Documented
@Constraint(validatedBy = CodePointLengthValidator.class)
@Retention(RetentionPolicy.RUNTIME)
@Target({
  ElementType.FIELD,
  ElementType.PARAMETER,
  ElementType.RECORD_COMPONENT,
  ElementType.TYPE_USE
})
public @interface CodePointLength {
  int min() default 0;

  int max();

  String message() default "trim后文本码点长度超出范围";

  Class<?>[] groups() default {};

  Class<? extends Payload>[] payload() default {};
}
