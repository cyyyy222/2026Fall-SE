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
public class AdminReviewController {
  private final ApiResponses responses;
  private final EventReviewService eventReviewService;

  public AdminReviewController(ApiResponses responses, EventReviewService eventReviewService) {
    this.responses = responses;
    this.eventReviewService = eventReviewService;
  }

  @ApiContract(
      id = "ADM-08",
      idempotency = false,
      query = {"overdueOnly", "page", "pageSize"})
  @PreAuthorize("hasAnyRole('ADMIN')")
  @GetMapping(value = "/admin/reviews")
  public ResponseEntity<ApiResponse<Views.ReviewQueue>> reviewQueue(
      @CurrentRequest RequestContext context, @Valid @ModelAttribute Queries.Reviews query) {
    return responses.toResponse(eventReviewService.reviewQueue(context, query));
  }

  @ApiContract(
      id = "ADM-09",
      idempotency = false,
      query = {})
  @PreAuthorize("hasAnyRole('ADMIN')")
  @GetMapping(value = "/admin/events/{eventId}")
  public ResponseEntity<ApiResponse<Views.EventManage>> eventDetail(
      @CurrentRequest RequestContext context, @PathVariable UUID eventId) {
    return responses.toResponse(eventReviewService.eventDetail(context, eventId));
  }

  @ApiContract(
      id = "ADM-10",
      idempotency = true,
      query = {})
  @PreAuthorize("hasAnyRole('ADMIN')")
  @PostMapping(value = "/admin/events/{eventId}/approval")
  public ResponseEntity<ApiResponse<Views.ReviewResult>> approve(
      @CurrentRequest RequestContext context,
      @PathVariable UUID eventId,
      @Valid @RequestBody Requests.Version request) {
    return responses.toResponse(eventReviewService.approve(context, eventId, request));
  }

  @ApiContract(
      id = "ADM-11",
      idempotency = true,
      query = {})
  @PreAuthorize("hasAnyRole('ADMIN')")
  @PostMapping(value = "/admin/events/{eventId}/rejection")
  public ResponseEntity<ApiResponse<Views.ReviewResult>> reject(
      @CurrentRequest RequestContext context,
      @PathVariable UUID eventId,
      @Valid @RequestBody Requests.VersionReason request) {
    return responses.toResponse(eventReviewService.reject(context, eventId, request));
  }

  @ApiContract(
      id = "ADM-28",
      idempotency = false,
      query = {"status", "name", "organizerId", "page", "pageSize"})
  @PreAuthorize("hasAnyRole('ADMIN')")
  @GetMapping(value = "/admin/events")
  public ResponseEntity<ApiResponse<PageResult<Views.EventManage>>> listEvents(
      @CurrentRequest RequestContext context, @Valid @ModelAttribute Queries.AdminEvents query) {
    return responses.toResponse(eventReviewService.listEvents(context, query));
  }
}
