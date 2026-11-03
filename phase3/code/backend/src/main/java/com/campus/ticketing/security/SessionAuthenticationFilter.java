package com.campus.ticketing.security;

import jakarta.servlet.*;
import jakarta.servlet.http.*;
import java.io.IOException;
import java.util.Arrays;
import org.springframework.web.filter.OncePerRequestFilter;

/** MySQL-backed, fixed-expiry sessions. No fallback authentication in offline mode. */
public class SessionAuthenticationFilter extends OncePerRequestFilter {
  private final JsonSecurityErrors errors;
  private final com.campus.ticketing.service.core.IdentityUseCases identity;

  public SessionAuthenticationFilter(
      JsonSecurityErrors errors, com.campus.ticketing.service.core.IdentityUseCases identity) {
    this.errors = errors;
    this.identity = identity;
  }

  @Override
  protected void doFilterInternal(
      HttpServletRequest req, HttpServletResponse res, FilterChain chain)
      throws ServletException, IOException {
    var cookie =
        req.getCookies() == null
            ? null
            : Arrays.stream(req.getCookies())
                .filter(c -> c.getName().equals("TICKET_SESSION") && !c.getValue().isEmpty())
                .findFirst()
                .orElse(null);
    if (cookie != null) {
      try {
        var principal = identity.authenticate(cookie.getValue());
        var authentication =
            new org.springframework.security.authentication.UsernamePasswordAuthenticationToken(
                principal,
                null,
                java.util.List.of(
                    new org.springframework.security.core.authority.SimpleGrantedAuthority(
                        "ROLE_" + principal.role())));
        var context =
            org.springframework.security.core.context.SecurityContextHolder.createEmptyContext();
        context.setAuthentication(authentication);
        org.springframework.security.core.context.SecurityContextHolder.setContext(context);
      } catch (com.campus.ticketing.common.ApiException e) {
        boolean publicRequest =
            req.getMethod().equals("GET")
                    && (req.getRequestURI().startsWith("/api/public/")
                        || req.getRequestURI().equals("/api/auth/csrf"))
                || req.getMethod().equals("POST")
                    && java.util.Set.of(
                            "/api/auth/login",
                            "/api/auth/register",
                            "/api/auth/recoveries",
                            "/api/auth/phone-codes",
                            "/api/auth/phone-login",
                            "/api/auth/password-reset")
                        .contains(req.getRequestURI());
        if (e.status() == 401 || e.status() == 403) {
          res.addHeader(
              "Set-Cookie",
              org.springframework.http.ResponseCookie.from("TICKET_SESSION", "")
                  .path("/api")
                  .httpOnly(true)
                  .sameSite("Lax")
                  .secure(req.isSecure())
                  .maxAge(java.time.Duration.ZERO)
                  .build()
                  .toString());
        }
        if (!publicRequest || (e.status() != 401 && e.status() != 403)) {
          errors.write(res, e.status(), e.code(), e.getMessage());
          return;
        }
      } catch (org.springframework.dao.DataAccessException e) {
        errors.write(res, 503, "DATABASE_UNAVAILABLE", "数据库会话暂不可用");
        return;
      }
    }
    chain.doFilter(req, res);
  }
}
