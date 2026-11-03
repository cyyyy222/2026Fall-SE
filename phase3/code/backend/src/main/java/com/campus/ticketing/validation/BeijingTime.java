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

  String message() default
      "时间格式不正确：请使用带时区的 ISO 时间（例如 2026-11-05T19:30:00+08:00），秒和毫秒必须为 0";

  Class<?>[] groups() default {};

  Class<? extends Payload>[] payload() default {};
}
