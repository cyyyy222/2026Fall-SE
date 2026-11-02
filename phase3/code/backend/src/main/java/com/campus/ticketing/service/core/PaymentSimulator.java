package com.campus.ticketing.service.core;

import org.springframework.stereotype.Component;

/** Local simulation only. Test doubles may fail attempts; no client-controlled failure flag. */
@Component
public class PaymentSimulator {
  public record Outcome(boolean success, String reason) {}

  public Outcome pay(long amountJiao) {
    return new Outcome(true, null);
  }

  public Outcome refund(long amountJiao) {
    return new Outcome(true, null);
  }
}
