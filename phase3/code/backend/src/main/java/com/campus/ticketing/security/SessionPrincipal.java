package com.campus.ticketing.security;

import com.campus.ticketing.enums.Role;
import java.time.Instant;
import java.util.UUID;

public record SessionPrincipal(
    UUID userId, Role role, boolean mustChangePassword, Instant expiresAt, UUID sessionId) {
  /**
   * Compatibility constructor for audit/system contexts and explicitly injected unit-test actors.
   */
  public SessionPrincipal(UUID userId, Role role, boolean mustChangePassword, Instant expiresAt) {
    this(userId, role, mustChangePassword, expiresAt, null);
  }
}
