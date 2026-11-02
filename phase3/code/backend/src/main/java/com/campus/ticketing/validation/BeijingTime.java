package com.campus.ticketing.validation;

import jakarta.validation.*;
import java.lang.annotation.*;

@Constraint(validatedBy = BeijingTimeValidator.class)
@Retention(RetentionPolicy.RUNTIME)
@Target({
  ElementType.FIELD,
  ElementType.PARAMETER,
  ElementType.RECORD_COMPONENT,
  ElementType.TYPE_USE
})
public @interface BeijingTime {
  int granularity() default 0;

  String message() default "时间须为+08:00且满足整小时或分钟粒度";

  Class<?>[] groups() default {};

  Class<? extends Payload>[] payload() default {};
}
