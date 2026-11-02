package com.campus.ticketing.common;

import jakarta.validation.ConstraintViolationException;
import java.util.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.*;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.multipart.support.MissingServletRequestPartException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

@RestControllerAdvice
public class GlobalExceptionHandler {
  private static final Logger LOG = LoggerFactory.getLogger(GlobalExceptionHandler.class);
  private final ApiResponses responses;
  private final com.campus.ticketing.service.core.FailureAudit audit;

  public GlobalExceptionHandler(
      ApiResponses responses, com.campus.ticketing.service.core.FailureAudit audit) {
    this.responses = responses;
    this.audit = audit;
  }

  private ResponseEntity<ApiErrorResponse> result(
      int status, String code, String msg, List<ApiErrorResponse.FieldError> fields) {
    var attrs = org.springframework.web.context.request.RequestContextHolder.getRequestAttributes();
    String api = "HTTP_REJECTED";
    if (attrs instanceof org.springframework.web.context.request.ServletRequestAttributes r) {
      Object value = r.getRequest().getAttribute("ticketing.apiId");
      if (value instanceof String id) api = id;
    }
    audit.record(api, code);
    return ResponseEntity.status(status).body(responses.error(code, msg, fields));
  }

  @ExceptionHandler(ApiException.class)
  ResponseEntity<ApiErrorResponse> api(ApiException e) {
    return result(e.status(), e.code(), e.getMessage(), List.of());
  }

  @ExceptionHandler(com.campus.ticketing.service.core.RateLimitService.Limited.class)
  ResponseEntity<ApiErrorResponse> rate(
      com.campus.ticketing.service.core.RateLimitService.Limited e) {
    audit.record("AUTH_RATE_LIMITED", e.code());
    return ResponseEntity.status(429)
        .header("Retry-After", Long.toString(e.retryAfter))
        .body(responses.error(e.code(), e.getMessage(), List.of()));
  }

  @ExceptionHandler({
    org.springframework.dao.CannotAcquireLockException.class,
    org.springframework.dao.PessimisticLockingFailureException.class
  })
  ResponseEntity<ApiErrorResponse> busy(Exception e) {
    return result(503, "DATABASE_BUSY", "数据库事务竞争，请用原请求键查询或重试", List.of());
  }

  @ExceptionHandler(org.springframework.dao.DataIntegrityViolationException.class)
  ResponseEntity<ApiErrorResponse> conflict(Exception e) {
    return result(409, "STATE_CONFLICT", "唯一性或关联约束发生冲突，请刷新确认原操作结果", List.of());
  }

  @ExceptionHandler(org.springframework.dao.DataAccessException.class)
  ResponseEntity<ApiErrorResponse> database(Exception e) {
    return result(503, "DATABASE_UNAVAILABLE", "数据库暂不可用", List.of());
  }

  @ExceptionHandler(MethodArgumentNotValidException.class)
  ResponseEntity<ApiErrorResponse> invalid(MethodArgumentNotValidException e) {
    return result(
        422,
        "VALIDATION_FAILED",
        "请求字段不合法",
        e.getBindingResult().getFieldErrors().stream()
            .map(
                f ->
                    new ApiErrorResponse.FieldError(
                        f.getField(),
                        Objects.requireNonNullElse(f.getCode(), "Invalid"),
                        f.getDefaultMessage()))
            .toList());
  }

  @ExceptionHandler({ConstraintViolationException.class, MethodArgumentTypeMismatchException.class})
  ResponseEntity<ApiErrorResponse> validation(Exception e) {
    return result(422, "VALIDATION_FAILED", "参数类型或取值不合法", List.of());
  }

  @ExceptionHandler({
    HttpMessageNotReadableException.class,
    MissingServletRequestParameterException.class,
    MissingServletRequestPartException.class
  })
  ResponseEntity<ApiErrorResponse> malformed(Exception e) {
    Throwable cause = e;
    while (cause != null) {
      if (cause instanceof com.fasterxml.jackson.databind.exc.InvalidFormatException)
        return result(422, "VALIDATION_FAILED", "字段格式或枚举不合法", List.of());
      cause = cause.getCause();
    }
    return result(400, "MALFORMED_REQUEST", "请求格式无法解析", List.of());
  }

  @ExceptionHandler(NoResourceFoundException.class)
  ResponseEntity<ApiErrorResponse> missing(Exception e) {
    return result(404, "RESOURCE_NOT_FOUND", "接口不存在", List.of());
  }

  @ExceptionHandler(MaxUploadSizeExceededException.class)
  ResponseEntity<ApiErrorResponse> size(Exception e) {
    return result(413, "FILE_TOO_LARGE", "文件超过大小限制", List.of());
  }

  @ExceptionHandler(HttpMediaTypeNotSupportedException.class)
  ResponseEntity<ApiErrorResponse> media(Exception e) {
    return result(415, "UNSUPPORTED_MEDIA_TYPE", "不支持的请求类型", List.of());
  }

  @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
  ResponseEntity<ApiErrorResponse> method(Exception e) {
    return result(405, "METHOD_NOT_ALLOWED", "请求方法不支持", List.of());
  }

  @ExceptionHandler(AccessDeniedException.class)
  ResponseEntity<ApiErrorResponse> denied(Exception e) {
    return result(403, "FORBIDDEN", "无权执行此操作", List.of());
  }

  @ExceptionHandler(Exception.class)
  ResponseEntity<ApiErrorResponse> unexpected(Exception e) {
    LOG.error("Unhandled API exception type={}", e.getClass().getName());
    return result(500, "INTERNAL_ERROR", "服务器内部错误，请保留traceId", List.of());
  }
}
