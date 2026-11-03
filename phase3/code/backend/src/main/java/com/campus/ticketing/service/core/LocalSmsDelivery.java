package com.campus.ticketing.service.core;

import static com.campus.ticketing.service.core.BusinessSupport.*;

import java.util.UUID;
import org.slf4j.*;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Service;

/** Explicit development-only SMS simulation; credentials are never returned through an API. */
@Service
public class LocalSmsDelivery {
  private static final Logger LOG = LoggerFactory.getLogger(LocalSmsDelivery.class);
  private final BusinessSupport b;
  private final boolean local;

  public LocalSmsDelivery(BusinessSupport b, Environment env) {
    this.b = b;
    local = env.getProperty("app.environment", "local").equals("local");
  }

  public void ensureAvailable() {
    if (!local) throw error(503, "SMS_UNAVAILABLE", "当前尚未配置真实短信通知服务");
  }

  public void recovery(String phone, String username, String password, UUID requestId) {
    ensureAvailable();
    b.afterCommit(
        () ->
            LOG.info(
                "[LOCAL_RECOVERY_SMS] phone={} account={} initialPassword={} recoveryId={}"
                    + " mustChangePassword=true",
                phone,
                username.replace("\r", "\\r").replace("\n", "\\n"),
                password,
                requestId));
  }
}
