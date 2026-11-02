package com.campus.ticketing.security;

import com.campus.ticketing.common.ApiResponses;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.*;
import java.io.IOException;
import java.util.List;
import org.springframework.stereotype.Component;

@Component
public class JsonSecurityErrors {
  private final ObjectMapper mapper;
  private final ApiResponses responses;
  private final com.campus.ticketing.service.core.FailureAudit audit;

  public JsonSecurityErrors(
      ObjectMapper mapper,
      ApiResponses responses,
      com.campus.ticketing.service.core.FailureAudit audit) {
    this.mapper = mapper;
    this.responses = responses;
    this.audit = audit;
  }

  public void write(HttpServletResponse response, int status, String code, String message)
      throws IOException {
    response.setStatus(status);
    response.setContentType("application/json;charset=UTF-8");
    audit.record("SECURITY_REJECTED", code);
    mapper.writeValue(response.getOutputStream(), responses.error(code, message, List.of()));
  }
}
