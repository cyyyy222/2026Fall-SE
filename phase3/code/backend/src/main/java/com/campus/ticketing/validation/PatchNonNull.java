package com.campus.ticketing.validation;

import jakarta.validation.*;
import java.lang.annotation.*;

@Constraint(validatedBy = PatchNonNullValidator.class)
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.FIELD, ElementType.PARAMETER, ElementType.RECORD_COMPONENT})
public @interface PatchNonNull {
  String message() default "出现时不能为null";

  Class<?>[] groups() default {};

  Class<? extends Payload>[] payload() default {};
}
