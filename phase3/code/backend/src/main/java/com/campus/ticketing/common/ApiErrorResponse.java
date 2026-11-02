package com.campus.ticketing.common;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;

public record ApiErrorResponse(
    String code,
    String message,
    Object data,
    List<FieldError> errors,
    Map<String, Object> context,
    String traceId,
    OffsetDateTime serverTime) {
  public record FieldError(String field, String code, String message) {}
}
