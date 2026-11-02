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
public class ReportController {
  private final ApiResponses responses;
  private final ReportService reportService;

  public ReportController(ApiResponses responses, ReportService reportService) {
    this.responses = responses;
    this.reportService = reportService;
  }

  @ApiContract(
      id = "ME-21",
      idempotency = true,
      query = {})
  @PreAuthorize("hasAnyRole('PARTICIPANT')")
  @PostMapping(value = "/me/reports")
  public ResponseEntity<ApiResponse<Views.ReportView>> createReport(
      @CurrentRequest RequestContext context, @Valid @RequestBody Requests.Report request) {
    return responses.toResponse(reportService.createReport(context, request));
  }

  @ApiContract(
      id = "ME-22",
      idempotency = false,
      query = {"status", "page", "pageSize"})
  @PreAuthorize("hasAnyRole('PARTICIPANT')")
  @GetMapping(value = "/me/reports")
  public ResponseEntity<ApiResponse<PageResult<Views.ReportView>>> listMine(
      @CurrentRequest RequestContext context, @Valid @ModelAttribute Queries.Reports query) {
    return responses.toResponse(reportService.listMine(context, query));
  }

  @ApiContract(
      id = "ME-23",
      idempotency = false,
      query = {})
  @PreAuthorize("hasAnyRole('PARTICIPANT')")
  @GetMapping(value = "/me/reports/{reportId}")
  public ResponseEntity<ApiResponse<Views.ReportView>> reportDetail(
      @CurrentRequest RequestContext context, @PathVariable UUID reportId) {
    return responses.toResponse(reportService.reportDetail(context, reportId));
  }
}
