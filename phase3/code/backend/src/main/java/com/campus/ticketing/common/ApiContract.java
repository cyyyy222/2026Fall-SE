package com.campus.ticketing.common;

import java.lang.annotation.*;

@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.METHOD)
public @interface ApiContract {
  String id();

  boolean idempotency() default false;

  String[] query() default {};
}
