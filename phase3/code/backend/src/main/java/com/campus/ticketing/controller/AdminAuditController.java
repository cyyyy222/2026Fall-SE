package com.campus.ticketing.controller;

import com.campus.ticketing.common.*;
import com.campus.ticketing.dto.queries.Queries;
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
public class AdminAuditController {
  private final ApiResponses responses;
  private final AuditQueryService auditQueryService;

  public AdminAuditController(ApiResponses responses, AuditQueryService auditQueryService) {
    this.responses = responses;
    this.auditQueryService = auditQueryService;
  }

  @ApiContract(
      id = "ADM-24",
      idempotency = false,
      query = {"from", "to", "action", "objectType", "objectId", "result", "page", "pageSize"})
  @PreAuthorize("hasAnyRole('ADMIN')")
  @GetMapping(value = "/admin/audit-logs")
  public ResponseEntity<ApiResponse<PageResult<Views.AuditLog>>> logs(
      @CurrentRequest RequestContext context, @Valid @ModelAttribute Queries.Audit query) {
    return responses.toResponse(auditQueryService.logs(context, query));
  }

  @ApiContract(
      id = "ADM-25",
      idempotency = false,
      query = {})
  @PreAuthorize("hasAnyRole('ADMIN')")
  @GetMapping(value = "/admin/audit-objects/{objectType}/{objectId}")
  public ResponseEntity<ApiResponse<Views.AuditObject>> singleObject(
      @CurrentRequest RequestContext context,
      @PathVariable AuditObjectType objectType,
      @PathVariable UUID objectId) {
    return responses.toResponse(auditQueryService.singleObject(context, objectType, objectId));
  }
}
