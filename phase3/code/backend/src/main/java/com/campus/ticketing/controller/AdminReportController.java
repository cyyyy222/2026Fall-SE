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
public class AdminReportController {
  private final ApiResponses responses;
  private final InvestigationService investigationService;

  public AdminReportController(ApiResponses responses, InvestigationService investigationService) {
    this.responses = responses;
    this.investigationService = investigationService;
  }

  @ApiContract(
      id = "ADM-19",
      idempotency = false,
      query = {"eventId", "status", "needsAttention", "page", "pageSize"})
  @PreAuthorize("hasAnyRole('ADMIN')")
  @GetMapping(value = "/admin/reports")
  public ResponseEntity<ApiResponse<PageResult<Views.ReportAdminRow>>> listReports(
      @CurrentRequest RequestContext context, @Valid @ModelAttribute Queries.AdminReports query) {
    return responses.toResponse(investigationService.listReports(context, query));
  }

  @ApiContract(
      id = "ADM-20",
      idempotency = true,
      query = {})
  @PreAuthorize("hasAnyRole('ADMIN')")
  @PostMapping(value = "/admin/investigations")
  public ResponseEntity<ApiResponse<Views.InvestigationResult>> investigate(
      @CurrentRequest RequestContext context, @Valid @RequestBody Requests.Investigation request) {
    return responses.toResponse(investigationService.investigate(context, request));
  }

  @ApiContract(
      id = "ADM-21",
      idempotency = false,
      query = {})
  @PreAuthorize("hasAnyRole('ADMIN')")
  @GetMapping(value = "/admin/investigations/{investigationId}")
  public ResponseEntity<ApiResponse<Views.InvestigationDetail>> investigationDetail(
      @CurrentRequest RequestContext context, @PathVariable UUID investigationId) {
    return responses.toResponse(investigationService.investigationDetail(context, investigationId));
  }

  @ApiContract(
      id = "ADM-30",
      idempotency = false,
      query = {"eventId", "conclusion", "page", "pageSize"})
  @PreAuthorize("hasAnyRole('ADMIN')")
  @GetMapping(value = "/admin/investigations")
  public ResponseEntity<ApiResponse<PageResult<Views.InvestigationResult>>> listInvestigations(
      @CurrentRequest RequestContext context, @Valid @ModelAttribute Queries.Investigations query) {
    return responses.toResponse(investigationService.listInvestigations(context, query));
  }
}
