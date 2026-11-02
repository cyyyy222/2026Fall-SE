package com.campus.ticketing.controller;

import com.campus.ticketing.common.*;
import com.campus.ticketing.dto.requests.Requests;
import com.campus.ticketing.dto.views.Views;
import com.campus.ticketing.enums.*;
import com.campus.ticketing.service.*;
import jakarta.validation.Valid;
import java.util.*;
import org.springframework.http.*;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api")
@Validated
public class AuthController {
  private final ApiResponses responses;
  private final AuthService authService;

  public AuthController(ApiResponses responses, AuthService authService) {
    this.responses = responses;
    this.authService = authService;
  }

  @ApiContract(id = "AUTH-01")
  @GetMapping("/auth/csrf")
  public ApiResponse<Views.Csrf> csrf(org.springframework.security.web.csrf.CsrfToken token) {
    return responses.success(new Views.Csrf(token.getToken(), token.getHeaderName()));
  }

  @ApiContract(
      id = "AUTH-02",
      idempotency = false,
      query = {})
  @PreAuthorize("permitAll()")
  @PostMapping(value = "/auth/login")
  public ResponseEntity<ApiResponse<Views.Identity>> login(
      @CurrentRequest RequestContext context, @Valid @RequestBody Requests.Login request) {
    return responses.toResponse(authService.login(context, request));
  }

  @ApiContract(
      id = "AUTH-03",
      idempotency = false,
      query = {})
  @PreAuthorize("permitAll()")
  @PostMapping(value = "/auth/register")
  public ResponseEntity<ApiResponse<Views.Registered>> register(
      @CurrentRequest RequestContext context, @Valid @RequestBody Requests.Register request) {
    return responses.toResponse(authService.register(context, request));
  }

  @ApiContract(
      id = "AUTH-04",
      idempotency = false,
      query = {})
  @PreAuthorize("permitAll()")
  @PostMapping(value = "/auth/recoveries")
  public ResponseEntity<ApiResponse<Views.RecoveryReceipt>> requestRecovery(
      @CurrentRequest RequestContext context, @Valid @RequestBody Requests.Recovery request) {
    return responses.toResponse(authService.requestRecovery(context, request));
  }

  @ApiContract(
      id = "AUTH-05",
      idempotency = false,
      query = {})
  @PreAuthorize("isAuthenticated()")
  @PostMapping(value = "/auth/logout")
  public ResponseEntity<ApiResponse<Views.LoggedOut>> logout(
      @CurrentRequest RequestContext context, @Valid @RequestBody Requests.Empty request) {
    return responses.toResponse(authService.logout(context, request));
  }
}
