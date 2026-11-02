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
public class MeAccountController {
  private final ApiResponses responses;
  private final AccountService accountService;

  public MeAccountController(ApiResponses responses, AccountService accountService) {
    this.responses = responses;
    this.accountService = accountService;
  }

  @ApiContract(
      id = "ME-01",
      idempotency = false,
      query = {})
  @PreAuthorize("isAuthenticated()")
  @GetMapping(value = "/me")
  public ResponseEntity<ApiResponse<Views.Identity>> currentUser(
      @CurrentRequest RequestContext context) {
    return responses.toResponse(accountService.currentUser(context));
  }

  @ApiContract(
      id = "ME-02",
      idempotency = true,
      query = {})
  @PreAuthorize("hasAnyRole('PARTICIPANT','ORGANIZER')")
  @PatchMapping(value = "/me/profile")
  public ResponseEntity<ApiResponse<Mutation<Views.UserView>>> updateProfile(
      @CurrentRequest RequestContext context, @Valid @RequestBody Requests.Profile request) {
    return responses.toResponse(accountService.updateProfile(context, request));
  }

  @ApiContract(
      id = "ME-03",
      idempotency = true,
      query = {})
  @PreAuthorize("isAuthenticated()")
  @PostMapping(value = "/me/password")
  public ResponseEntity<ApiResponse<Views.ChangedPassword>> changePassword(
      @CurrentRequest RequestContext context, @Valid @RequestBody Requests.Password request) {
    return responses.toResponse(accountService.changePassword(context, request));
  }

  @ApiContract(
      id = "ME-04",
      idempotency = false,
      query = {})
  @PreAuthorize("hasAnyRole('PARTICIPANT','ORGANIZER')")
  @GetMapping(value = "/me/closure-eligibility")
  public ResponseEntity<ApiResponse<Views.ClosureEligibility>> closureEligibility(
      @CurrentRequest RequestContext context) {
    return responses.toResponse(accountService.closureEligibility(context));
  }

  @ApiContract(
      id = "ME-05",
      idempotency = true,
      query = {})
  @PreAuthorize("hasAnyRole('PARTICIPANT','ORGANIZER')")
  @PostMapping(value = "/me/closure")
  public ResponseEntity<ApiResponse<Views.ClosedAccount>> closeAccount(
      @CurrentRequest RequestContext context, @Valid @RequestBody Requests.Closure request) {
    return responses.toResponse(accountService.closeAccount(context, request));
  }
}
