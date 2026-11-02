package com.campus.ticketing.service.core;

import com.campus.ticketing.common.RequestContext;
import com.campus.ticketing.security.SessionPrincipal;
import org.slf4j.MDC;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

/**
 * Called by error handling only after the business transaction has unwound. Best effort on DB
 * outage.
 */
@Component
public class FailureAudit {
  private final BusinessSupport b;

  public FailureAudit(BusinessSupport b) {
    this.b = b;
  }

  public void record(String action, String code) {
    var auth = SecurityContextHolder.getContext().getAuthentication();
    SessionPrincipal principal =
        auth != null && auth.getPrincipal() instanceof SessionPrincipal p ? p : null;
    try {
      b.write(
          () -> {
            b.audit(
                new RequestContext(principal, null, MDC.get("traceId")),
                action,
                "API",
                null,
                "FAILURE",
                code,
                null);
            return null;
          });
    } catch (Exception unavailable) {
      /* Never replace the original error or claim the log was persisted. */
    }
  }
}
