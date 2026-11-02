package com.campus.ticketing.controller;

import com.campus.ticketing.common.*;
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
public class CheckerController {
  private final ApiResponses responses;
  private final CheckInService checkInService;

  public CheckerController(ApiResponses responses, CheckInService checkInService) {
    this.responses = responses;
    this.checkInService = checkInService;
  }

  @ApiContract(
      id = "CHK-01",
      idempotency = false,
      query = {})
  @PreAuthorize("hasAnyRole('CHECKER')")
  @GetMapping(value = "/checker/context")
  public ResponseEntity<ApiResponse<Views.CheckerContext>> context(
      @CurrentRequest RequestContext context) {
    return responses.toResponse(checkInService.context(context));
  }

  @ApiContract(
      id = "CHK-02",
      idempotency = false,
      query = {})
  @PreAuthorize("hasAnyRole('CHECKER')")
  @PostMapping(value = "/checker/ticket-previews")
  public ResponseEntity<ApiResponse<Views.TicketPreview>> previewTicket(
      @CurrentRequest RequestContext context, @Valid @RequestBody Requests.Ticket request) {
    return responses.toResponse(checkInService.previewTicket(context, request));
  }

  @ApiContract(
      id = "CHK-03",
      idempotency = false,
      query = {})
  @PreAuthorize("hasAnyRole('CHECKER')")
  @PostMapping(value = "/checker/checkins")
  public ResponseEntity<ApiResponse<Views.CheckInResult>> confirmCheckIn(
      @CurrentRequest RequestContext context, @Valid @RequestBody Requests.Ticket request) {
    return responses.toResponse(checkInService.confirmCheckIn(context, request));
  }

  @ApiContract(
      id = "CHK-04",
      idempotency = true,
      query = {})
  @PreAuthorize("hasAnyRole('CHECKER')")
  @PostMapping(value = "/checker/sessions/{sessionId}/absence-reconciliation")
  public ResponseEntity<ApiResponse<Views.Reconciliation>> reconcileAbsence(
      @CurrentRequest RequestContext context,
      @PathVariable UUID sessionId,
      @Valid @RequestBody Requests.Empty request) {
    return responses.toResponse(checkInService.reconcileAbsence(context, sessionId, request));
  }
}
