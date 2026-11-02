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
public class StatisticsController {
  private final ApiResponses responses;
  private final StatisticsService statisticsService;

  public StatisticsController(ApiResponses responses, StatisticsService statisticsService) {
    this.responses = responses;
    this.statisticsService = statisticsService;
  }

  @ApiContract(
      id = "MGT-05",
      idempotency = false,
      query = {"sessionId"})
  @PreAuthorize("hasAnyRole('ORGANIZER','CHECKER','ADMIN')")
  @GetMapping(value = "/management/events/{eventId}/live-counts")
  public ResponseEntity<ApiResponse<Views.Counts>> liveCounts(
      @CurrentRequest RequestContext context,
      @PathVariable UUID eventId,
      @Valid @ModelAttribute Queries.Session query) {
    return responses.toResponse(statisticsService.liveCounts(context, eventId, query));
  }

  @ApiContract(
      id = "MGT-06",
      idempotency = false,
      query = {"sessionId"})
  @PreAuthorize("hasAnyRole('ORGANIZER','CHECKER','ADMIN')")
  @GetMapping(value = "/management/events/{eventId}/statistics")
  public ResponseEntity<ApiResponse<Views.StatisticsProjection>> statistics(
      @CurrentRequest RequestContext context,
      @PathVariable UUID eventId,
      @Valid @ModelAttribute Queries.Session query) {
    return responses.toResponse(statisticsService.statistics(context, eventId, query));
  }

  @ApiContract(
      id = "MGT-07",
      idempotency = false,
      query = {"sessionId"})
  @PreAuthorize("hasAnyRole('ORGANIZER','ADMIN')")
  @GetMapping(value = "/management/events/{eventId}/payment-summary")
  public ResponseEntity<ApiResponse<Views.PaymentSummary>> paymentSummary(
      @CurrentRequest RequestContext context,
      @PathVariable UUID eventId,
      @Valid @ModelAttribute Queries.Session query) {
    return responses.toResponse(statisticsService.paymentSummary(context, eventId, query));
  }
}
