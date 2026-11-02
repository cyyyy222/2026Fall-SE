package com.campus.ticketing.common;

import com.campus.ticketing.security.SessionPrincipal;
import java.util.UUID;

/** Internal context only. Never populated from JSON userId/role or returned to the client. */
public record RequestContext(
    SessionPrincipal actor, UUID idempotencyKey, String traceId, String clientAddress) {
  public RequestContext(SessionPrincipal actor, UUID idempotencyKey, String traceId) {
    this(actor, idempotencyKey, traceId, "unavailable");
  }
}
