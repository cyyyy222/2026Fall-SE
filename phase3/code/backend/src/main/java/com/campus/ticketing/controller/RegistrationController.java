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
public class RegistrationController {
  private final ApiResponses responses;
  private final RegistrationService registrationService;

  public RegistrationController(ApiResponses responses, RegistrationService registrationService) {
    this.responses = responses;
    this.registrationService = registrationService;
  }

  @ApiContract(
      id = "ME-06",
      idempotency = false,
      query = {})
  @PreAuthorize("hasAnyRole('PARTICIPANT')")
  @PostMapping(value = "/me/registrations")
  public ResponseEntity<ApiResponse<Views.RegistrationResult>> register(
      @CurrentRequest RequestContext context, @Valid @RequestBody Requests.Registration request) {
    return responses.toResponse(registrationService.register(context, request));
  }

  @ApiContract(
      id = "ME-09",
      idempotency = false,
      query = {})
  @PreAuthorize("hasAnyRole('PARTICIPANT')")
  @GetMapping(value = "/me/registration-requests/{requestId}")
  public ResponseEntity<ApiResponse<Views.RegistrationResult>> findByRequest(
      @CurrentRequest RequestContext context, @PathVariable UUID requestId) {
    return responses.toResponse(registrationService.findByRequest(context, requestId));
  }
}
