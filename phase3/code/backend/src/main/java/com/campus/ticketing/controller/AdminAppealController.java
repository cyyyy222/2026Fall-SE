package com.campus.ticketing.controller;

import com.campus.ticketing.common.*;
import com.campus.ticketing.dto.queries.Queries;
import com.campus.ticketing.dto.requests.Requests;
import com.campus.ticketing.dto.views.Views;
import com.campus.ticketing.service.AppealService;
import jakarta.validation.Valid;
import java.util.*;
import org.springframework.http.*;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api")
@Validated
public class AdminAppealController {
  private final ApiResponses responses;
  private final AppealService appealService;

  public AdminAppealController(ApiResponses responses, AppealService appealService) {
    this.responses = responses;
    this.appealService = appealService;
  }

  @ApiContract(
      id = "ADM-32",
      idempotency = false,
      query = {"status", "page", "pageSize"})
  @PreAuthorize("hasAnyRole('ADMIN')")
  @GetMapping(value = "/admin/restriction-appeals")
  public ResponseEntity<ApiResponse<PageResult<Views.RestrictionAppealView>>> list(
      @CurrentRequest RequestContext context,
      @Valid @ModelAttribute Queries.RestrictionAppeals query) {
    return responses.toResponse(appealService.listAppeals(context, query));
  }

  @ApiContract(
      id = "ADM-33",
      idempotency = true,
      query = {})
  @PreAuthorize("hasAnyRole('ADMIN')")
  @PostMapping(value = "/admin/restriction-appeals/{appealId}/resolution")
  public ResponseEntity<ApiResponse<Views.RestrictionAppealView>> resolve(
      @CurrentRequest RequestContext context,
      @PathVariable UUID appealId,
      @Valid @RequestBody Requests.ResolveAppeal request) {
    return responses.toResponse(appealService.resolveAppeal(context, appealId, request));
  }
}
