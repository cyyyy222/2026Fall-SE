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
public class AdminTerminationController {
  private final ApiResponses responses;
  private final EventTerminationService eventTerminationService;

  public AdminTerminationController(
      ApiResponses responses, EventTerminationService eventTerminationService) {
    this.responses = responses;
    this.eventTerminationService = eventTerminationService;
  }

  @ApiContract(
      id = "ADM-15",
      idempotency = true,
      query = {})
  @PreAuthorize("hasAnyRole('ADMIN')")
  @PostMapping(value = "/admin/events/{eventId}/takedown")
  public ResponseEntity<ApiResponse<Views.TerminationResult>> takedown(
      @CurrentRequest RequestContext context,
      @PathVariable UUID eventId,
      @Valid @RequestBody Requests.Takedown request) {
    return responses.toResponse(eventTerminationService.takedown(context, eventId, request));
  }
}
