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
public class AdminRestrictionController {
  private final ApiResponses responses;
  private final RestrictionService restrictionService;

  public AdminRestrictionController(ApiResponses responses, RestrictionService restrictionService) {
    this.responses = responses;
    this.restrictionService = restrictionService;
  }

  @ApiContract(
      id = "ADM-22",
      idempotency = false,
      query = {"kind", "page", "pageSize"})
  @PreAuthorize("hasAnyRole('ADMIN')")
  @GetMapping(value = "/admin/restriction-candidates")
  public ResponseEntity<ApiResponse<PageResult<Views.RestrictionCandidate>>> candidates(
      @CurrentRequest RequestContext context, @Valid @ModelAttribute Queries.Candidates query) {
    return responses.toResponse(restrictionService.candidates(context, query));
  }

  @ApiContract(
      id = "ADM-23",
      idempotency = true,
      query = {})
  @PreAuthorize("hasAnyRole('ADMIN')")
  @PostMapping(value = "/admin/registration-restrictions")
  public ResponseEntity<ApiResponse<Views.RestrictionView>> restrictRegistration(
      @CurrentRequest RequestContext context, @Valid @RequestBody Requests.Restriction request) {
    return responses.toResponse(restrictionService.restrictRegistration(context, request));
  }

  @ApiContract(
      id = "ADM-29",
      idempotency = false,
      query = {"kind"})
  @PreAuthorize("hasAnyRole('ADMIN')")
  @GetMapping(value = "/admin/restriction-candidates/{phoneIdentityId}")
  public ResponseEntity<ApiResponse<Views.RestrictionEvidence>> candidateEvidence(
      @CurrentRequest RequestContext context,
      @PathVariable UUID phoneIdentityId,
      @Valid @ModelAttribute Queries.Candidate query) {
    return responses.toResponse(
        restrictionService.candidateEvidence(context, phoneIdentityId, query));
  }
}
