package com.campus.ticketing.controller;

import com.campus.ticketing.common.*;
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
public class AppealController {
  private final ApiResponses responses;
  private final AppealService appealService;

  public AppealController(ApiResponses responses, AppealService appealService) {
    this.responses = responses;
    this.appealService = appealService;
  }

  @ApiContract(
      id = "ME-28",
      idempotency = false,
      query = {})
  @PreAuthorize("hasAnyRole('PARTICIPANT')")
  @GetMapping(value = "/me/restrictions")
  public ResponseEntity<ApiResponse<List<Views.RestrictionView>>> myRestrictions(
      @CurrentRequest RequestContext context) {
    return responses.toResponse(appealService.myRestrictions(context));
  }

  @ApiContract(
      id = "ME-29",
      idempotency = false,
      query = {})
  @PreAuthorize("hasAnyRole('PARTICIPANT')")
  @GetMapping(value = "/me/restriction-appeals")
  public ResponseEntity<ApiResponse<List<Views.RestrictionAppealView>>> myAppeals(
      @CurrentRequest RequestContext context) {
    return responses.toResponse(appealService.myAppeals(context));
  }

  @ApiContract(
      id = "ME-30",
      idempotency = true,
      query = {})
  @PreAuthorize("hasAnyRole('PARTICIPANT')")
  @PostMapping(value = "/me/restriction-appeals")
  public ResponseEntity<ApiResponse<Views.RestrictionAppealView>> createAppeal(
      @CurrentRequest RequestContext context, @Valid @RequestBody Requests.RestrictionAppeal request) {
    return responses.toResponse(appealService.createAppeal(context, request));
  }
}
