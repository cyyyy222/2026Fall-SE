package com.campus.ticketing.controller;

import com.campus.ticketing.common.*;
import com.campus.ticketing.dto.queries.Queries;
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
public class AdminAccountController {
  private final ApiResponses responses;
  private final AccountAdminService accountAdminService;

  public AdminAccountController(ApiResponses responses, AccountAdminService accountAdminService) {
    this.responses = responses;
    this.accountAdminService = accountAdminService;
  }

  @ApiContract(
      id = "ADM-01",
      idempotency = false,
      query = {"role", "username", "eventId", "sessionId", "enabled", "page", "pageSize"})
  @PreAuthorize("hasAnyRole('ADMIN')")
  @GetMapping(value = "/admin/accounts")
  public ResponseEntity<ApiResponse<PageResult<Views.ManagedAccount>>> listAccounts(
      @CurrentRequest RequestContext context, @Valid @ModelAttribute Queries.Accounts query) {
    return responses.toResponse(accountAdminService.listAccounts(context, query));
  }

  @ApiContract(
      id = "ADM-02",
      idempotency = true,
      query = {})
  @PreAuthorize("hasAnyRole('ADMIN')")
  @PostMapping(value = "/admin/accounts")
  public ResponseEntity<ApiResponse<Views.ManagedAccount>> createAccount(
      @CurrentRequest RequestContext context, @Valid @RequestBody Requests.CreateAccount request) {
    return responses.toResponse(accountAdminService.createAccount(context, request));
  }

  @ApiContract(
      id = "ADM-03",
      idempotency = true,
      query = {})
  @PreAuthorize("hasAnyRole('ADMIN')")
  @PatchMapping(value = "/admin/accounts/{accountId}")
  public ResponseEntity<ApiResponse<Mutation<Views.ManagedAccount>>> updateAccount(
      @CurrentRequest RequestContext context,
      @PathVariable UUID accountId,
      @Valid @RequestBody Requests.UpdateAccount request) {
    return responses.toResponse(accountAdminService.updateAccount(context, accountId, request));
  }

  @ApiContract(
      id = "ADM-04",
      idempotency = true,
      query = {"expectedVersion"})
  @PreAuthorize("hasAnyRole('ADMIN')")
  @DeleteMapping(value = "/admin/accounts/{accountId}")
  public ResponseEntity<ApiResponse<Mutation<Views.ManagedAccount>>> deleteAccount(
      @CurrentRequest RequestContext context,
      @PathVariable UUID accountId,
      @Valid @ModelAttribute Queries.Version query) {
    return responses.toResponse(accountAdminService.deleteAccount(context, accountId, query));
  }

  @ApiContract(
      id = "ADM-05",
      idempotency = true,
      query = {})
  @PreAuthorize("hasAnyRole('ADMIN')")
  @PostMapping(value = "/admin/accounts/{accountId}/password-reset")
  public ResponseEntity<ApiResponse<Views.ResetPassword>> resetPassword(
      @CurrentRequest RequestContext context,
      @PathVariable UUID accountId,
      @Valid @RequestBody Requests.ResetPassword request) {
    return responses.toResponse(accountAdminService.resetPassword(context, accountId, request));
  }

  @ApiContract(
      id = "ADM-06",
      idempotency = false,
      query = {"status", "page", "pageSize"})
  @PreAuthorize("hasAnyRole('ADMIN')")
  @GetMapping(value = "/admin/recoveries")
  public ResponseEntity<ApiResponse<PageResult<Views.RecoveryView>>> listRecoveries(
      @CurrentRequest RequestContext context, @Valid @ModelAttribute Queries.Recoveries query) {
    return responses.toResponse(accountAdminService.listRecoveries(context, query));
  }

  @ApiContract(
      id = "ADM-07",
      idempotency = true,
      query = {})
  @PreAuthorize("hasAnyRole('ADMIN')")
  @PostMapping(value = "/admin/recoveries/{requestId}/resolution")
  public ResponseEntity<ApiResponse<Views.RecoveryView>> resolveRecovery(
      @CurrentRequest RequestContext context,
      @PathVariable UUID requestId,
      @Valid @RequestBody Requests.ResolveRecovery request) {
    return responses.toResponse(accountAdminService.resolveRecovery(context, requestId, request));
  }
}
