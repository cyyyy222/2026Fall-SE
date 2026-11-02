package com.campus.ticketing.interceptor;

import com.campus.ticketing.common.*;
import com.campus.ticketing.security.SessionPrincipal;
import jakarta.servlet.http.*;
import java.util.*;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.HandlerInterceptor;

public class ApiContractInterceptor implements HandlerInterceptor {
  private static final Set<String> FORCE_ALLOWED = Set.of("AUTH-01", "AUTH-05", "ME-01", "ME-03");

  @Override
  public boolean preHandle(HttpServletRequest req, HttpServletResponse res, Object handler) {
    if (!(handler instanceof HandlerMethod h)) return true;
    ApiContract c = h.getMethodAnnotation(ApiContract.class);
    if (c == null) return true;
    req.setAttribute("ticketing.apiId", c.id());
    var authentication = SecurityContextHolder.getContext().getAuthentication();
    if (authentication != null
        && authentication.getPrincipal() instanceof SessionPrincipal p
        && p.mustChangePassword()
        && !FORCE_ALLOWED.contains(c.id())
        && !c.id().startsWith("PUB-"))
      throw new ApiException(403, "PASSWORD_CHANGE_REQUIRED", "请先修改初始密码");
    Set<String> allowed = Set.of(c.query());
    for (String key : req.getParameterMap().keySet()) {
      if (!allowed.contains(key))
        throw new ApiException(422, "VALIDATION_FAILED", "不支持的查询字段：" + key);
      if (req.getParameterValues(key).length != 1)
        throw new ApiException(422, "VALIDATION_FAILED", "查询字段不可重复：" + key);
    }
    for (var parameter : h.getMethodParameters()) {
      Class<?> type = parameter.getParameterType();
      if (type.isRecord()
          && parameter.hasParameterAnnotation(
              org.springframework.web.bind.annotation.ModelAttribute.class)) {
        for (var field : type.getRecordComponents()) {
          String value = req.getParameter(field.getName());
          if (value == null) continue;
          if (value.isBlank() || value.equals("null"))
            throw new ApiException(422, "VALIDATION_FAILED", "查询字段不能为空或null：" + field.getName());
          if (field.getType() == Boolean.class && !value.equals("true") && !value.equals("false"))
            throw new ApiException(422, "VALIDATION_FAILED", "布尔查询字段只接受true/false");
        }
      }
    }
    if (c.idempotency()) {
      String key = req.getHeader("Idempotency-Key");
      if (key == null
          || !key.matches(
              "[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}"))
        throw new ApiException(422, "VALIDATION_FAILED", "Idempotency-Key必须为UUID");
    }
    return true;
  }
}
