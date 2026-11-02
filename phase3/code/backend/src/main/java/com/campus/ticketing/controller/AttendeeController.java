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
public class AttendeeController {
  private final ApiResponses responses;
  private final AttendeeService attendeeService;

  public AttendeeController(ApiResponses responses, AttendeeService attendeeService) {
    this.responses = responses;
    this.attendeeService = attendeeService;
  }

  @ApiContract(
      id = "MGT-01",
      idempotency = false,
      query = {"sessionId", "ticketTypeId", "orderStatus", "checkinStatus", "page", "pageSize"})
  @PreAuthorize("hasAnyRole('ORGANIZER','CHECKER','ADMIN')")
  @GetMapping(value = "/management/events/{eventId}/attendees")
  public ResponseEntity<ApiResponse<PageResult<Views.AttendeeRow>>> listAttendees(
      @CurrentRequest RequestContext context,
      @PathVariable UUID eventId,
      @Valid @ModelAttribute Queries.Attendees query) {
    return responses.toResponse(attendeeService.listAttendees(context, eventId, query));
  }
}
