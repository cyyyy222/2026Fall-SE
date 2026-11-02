package com.campus.ticketing.common;

import java.time.*;
import java.util.*;
import org.slf4j.MDC;
import org.springframework.stereotype.Component;

@Component
public class ApiResponses {
  private final Clock clock;

  public ApiResponses(Clock clock) {
    this.clock = clock;
  }

  public <T> ApiResponse<T> success(T data) {
    return result("OK", "操作成功", data);
  }

  public <T> ApiResponse<T> result(String code, String message, T data) {
    return new ApiResponse<>(code, message, data, trace(), now());
  }

  public ApiErrorResponse error(
      String code, String message, List<ApiErrorResponse.FieldError> fields) {
    return new ApiErrorResponse(code, message, null, fields, Map.of(), trace(), now());
  }

  public OffsetDateTime now() {
    return OffsetDateTime.ofInstant(clock.instant(), ZoneOffset.ofHours(8));
  }

  public <T> org.springframework.http.ResponseEntity<ApiResponse<T>> toResponse(
      UseCaseResult<T> result) {
    var builder = org.springframework.http.ResponseEntity.status(result.status());
    for (var cookie : result.cookies())
      builder.header(org.springframework.http.HttpHeaders.SET_COOKIE, cookie.toString());
    return builder.body(success(result.data()));
  }

  public org.springframework.http.ResponseEntity<org.springframework.core.io.Resource> download(
      DownloadResult d) {
    var disposition =
        d.attachment()
            ? org.springframework.http.ContentDisposition.attachment()
            : org.springframework.http.ContentDisposition.inline();
    return org.springframework.http.ResponseEntity.ok()
        .contentType(d.contentType())
        .header(
            org.springframework.http.HttpHeaders.CONTENT_DISPOSITION,
            disposition
                .filename(d.filename(), java.nio.charset.StandardCharsets.UTF_8)
                .build()
                .toString())
        .body(d.resource());
  }

  private String trace() {
    return Objects.requireNonNullElse(MDC.get("traceId"), "unavailable");
  }
}
