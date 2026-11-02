package com.campus.ticketing.config;

import com.campus.ticketing.security.*;
import org.springframework.context.annotation.*;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.web.*;
import org.springframework.security.web.authentication.AnonymousAuthenticationFilter;
import org.springframework.security.web.csrf.*;

@Configuration
@EnableMethodSecurity
public class SecurityConfig {
  @Bean
  SecurityFilterChain security(
      HttpSecurity http,
      JsonSecurityErrors errors,
      com.campus.ticketing.service.core.IdentityUseCases identity)
      throws Exception {
    var repository = new CookieCsrfTokenRepository();
    repository.setCookiePath("/api");
    repository.setHeaderName("X-CSRF-TOKEN");
    repository.setCookieCustomizer(c -> c.httpOnly(true).sameSite("Lax"));
    var handler = new CsrfTokenRequestAttributeHandler();
    handler.setCsrfRequestAttributeName(null);
    // Authentication is restored from the MySQL cookie on every request. HttpSession's
    // implicit "new authentication" detection must not rotate CSRF on each restored request.
    http.sessionManagement(s -> s.disable())
        .securityContext(
            c ->
                c.securityContextRepository(
                    new org.springframework.security.web.context.NullSecurityContextRepository()))
        .requestCache(c -> c.disable())
        .formLogin(c -> c.disable())
        .httpBasic(c -> c.disable())
        .logout(c -> c.disable())
        .csrf(c -> c.csrfTokenRepository(repository).csrfTokenRequestHandler(handler))
        .exceptionHandling(
            c ->
                c.authenticationEntryPoint(
                        (req, res, e) -> errors.write(res, 401, "AUTH_REQUIRED", "请先登录"))
                    .accessDeniedHandler(
                        (req, res, e) ->
                            errors.write(
                                res,
                                403,
                                e instanceof CsrfException ? "CSRF_INVALID" : "FORBIDDEN",
                                e instanceof CsrfException ? "CSRF令牌无效" : "无权执行此操作")))
        .authorizeHttpRequests(
            c ->
                c.requestMatchers(
                        org.springframework.http.HttpMethod.GET,
                        "/api/auth/csrf",
                        "/api/public/**",
                        "/api/system/health")
                    .permitAll()
                    .requestMatchers(
                        org.springframework.http.HttpMethod.POST,
                        "/api/auth/login",
                        "/api/auth/register",
                        "/api/auth/recoveries")
                    .permitAll()
                    .anyRequest()
                    .authenticated())
        .addFilterBefore(
            new SessionAuthenticationFilter(errors, identity), AnonymousAuthenticationFilter.class);
    return http.build();
  }
}
