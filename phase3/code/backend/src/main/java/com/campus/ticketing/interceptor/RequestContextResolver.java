package com.campus.ticketing.interceptor;

import com.campus.ticketing.common.*;
import com.campus.ticketing.security.SessionPrincipal;
import java.util.UUID;
import org.slf4j.MDC;
import org.springframework.core.MethodParameter;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.support.WebDataBinderFactory;
import org.springframework.web.context.request.NativeWebRequest;
import org.springframework.web.method.support.*;

public class RequestContextResolver implements HandlerMethodArgumentResolver {
  @Override
  public boolean supportsParameter(MethodParameter p) {
    return p.hasParameterAnnotation(CurrentRequest.class)
        && p.getParameterType() == RequestContext.class;
  }

  @Override
  public Object resolveArgument(
      MethodParameter p, ModelAndViewContainer m, NativeWebRequest r, WebDataBinderFactory b) {
    var auth = SecurityContextHolder.getContext().getAuthentication();
    var actor =
        auth != null && auth.getPrincipal() instanceof SessionPrincipal principal
            ? principal
            : null;
    String key = r.getHeader("Idempotency-Key");
    UUID uuid = null;
    if (key != null) {
      try {
        uuid = UUID.fromString(key);
      } catch (IllegalArgumentException ignored) {
        throw new ApiException(422, "VALIDATION_FAILED", "Idempotency-Key必须为UUID");
      }
    }
    var nativeRequest = r.getNativeRequest(jakarta.servlet.http.HttpServletRequest.class);
    return new RequestContext(
        actor,
        uuid,
        MDC.get("traceId"),
        nativeRequest == null ? "unavailable" : nativeRequest.getRemoteAddr());
  }
}
