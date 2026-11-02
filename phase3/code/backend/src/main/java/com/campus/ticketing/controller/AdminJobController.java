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
public class AdminJobController {
  private final ApiResponses responses;
  private final JobQueryService jobQueryService;

  public AdminJobController(ApiResponses responses, JobQueryService jobQueryService) {
    this.responses = responses;
    this.jobQueryService = jobQueryService;
  }

  @ApiContract(
      id = "ADM-26",
      idempotency = false,
      query = {"eventId", "type", "status", "overdue", "page", "pageSize"})
  @PreAuthorize("hasAnyRole('ADMIN')")
  @GetMapping(value = "/admin/jobs")
  public ResponseEntity<ApiResponse<PageResult<Views.JobView>>> listJobs(
      @CurrentRequest RequestContext context, @Valid @ModelAttribute Queries.Jobs query) {
    return responses.toResponse(jobQueryService.listJobs(context, query));
  }

  @ApiContract(
      id = "ADM-27",
      idempotency = false,
      query = {})
  @PreAuthorize("hasAnyRole('ADMIN')")
  @GetMapping(value = "/admin/jobs/{jobId}")
  public ResponseEntity<ApiResponse<Views.JobView>> jobDetail(
      @CurrentRequest RequestContext context, @PathVariable UUID jobId) {
    return responses.toResponse(jobQueryService.jobDetail(context, jobId));
  }
}
