package com.campus.ticketing;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.campus.ticketing.common.ApiException;
import com.campus.ticketing.security.*;
import com.campus.ticketing.service.core.IdentityUseCases;
import jakarta.servlet.FilterChain;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.*;

class SessionAuthenticationFilterTest {
  private MockHttpServletResponse check(String method, String path, int status, boolean allowed)
      throws Exception {
    var identity = mock(IdentityUseCases.class);
    when(identity.authenticate("stale-session"))
        .thenThrow(
            new ApiException(
                status, status == 503 ? "DATABASE_UNAVAILABLE" : "SESSION_REVOKED", "rejected"));
    var errors = mock(JsonSecurityErrors.class);
    doAnswer(
            invocation -> {
              ((jakarta.servlet.http.HttpServletResponse) invocation.getArgument(0))
                  .setStatus(invocation.getArgument(1));
              return null;
            })
        .when(errors)
        .write(any(), anyInt(), anyString(), anyString());
    var filter = new SessionAuthenticationFilter(errors, identity);
    var request = new MockHttpServletRequest(method, path);
    request.setCookies(new Cookie("TICKET_SESSION", "stale-session"));
    var response = new MockHttpServletResponse();
    var chain = mock(FilterChain.class);
    filter.doFilter(request, response, chain);
    if (allowed) {
      verify(chain).doFilter(request, response);
      verifyNoInteractions(errors);
    } else {
      verifyNoInteractions(chain);
      assertThat(response.getStatus()).isEqualTo(status);
    }
    return response;
  }

  @Test
  void staleCookieCannotBlockLoginRegistrationOrRecovery() throws Exception {
    for (String path :
        java.util.List.of("/api/auth/login", "/api/auth/register", "/api/auth/recoveries")) {
      for (int status : java.util.List.of(401, 403)) {
        var response = check("POST", path, status, true);
        assertThat(response.getHeader("Set-Cookie"))
            .contains("TICKET_SESSION=", "Path=/api", "Max-Age=0", "HttpOnly");
      }
    }
  }

  @Test
  void privateRequestsStillRejectAndClearStaleCookies() throws Exception {
    var response = check("POST", "/api/me/registrations", 401, false);
    assertThat(response.getHeader("Set-Cookie")).contains("Max-Age=0");
  }

  @Test
  void publicReadsClearStaleCookiesAndContinue() throws Exception {
    check("GET", "/api/auth/csrf", 401, true);
    check("GET", "/api/public/sessions", 403, true);
  }

  @Test
  void databaseFailuresNeverBecomeAnonymousLoginAttempts() throws Exception {
    var response = check("POST", "/api/auth/login", 503, false);
    assertThat(response.getHeader("Set-Cookie")).isNull();
  }
}
