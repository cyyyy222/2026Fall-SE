package com.campus.ticketing.controller;

import com.campus.ticketing.common.*;
import com.campus.ticketing.dto.queries.Queries;
import com.campus.ticketing.dto.views.Views;
import com.campus.ticketing.enums.*;
import com.campus.ticketing.service.*;
import jakarta.validation.Valid;
import java.util.*;
import org.springframework.core.io.Resource;
import org.springframework.http.*;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api")
@Validated
public class PublicEventController {
  private final ApiResponses responses;
  private final PublicEventService publicEventService;

  public PublicEventController(ApiResponses responses, PublicEventService publicEventService) {
    this.responses = responses;
    this.publicEventService = publicEventService;
  }

  @ApiContract(
      id = "PUB-02",
      idempotency = false,
      query = {
        "eventName",
        "categoryId",
        "startsFrom",
        "startsTo",
        "location",
        "ticketTypeName",
        "minPriceJiao",
        "maxPriceJiao",
        "salesStatus",
        "sortBy",
        "sortDirection",
        "page",
        "pageSize"
      })
  @PreAuthorize("permitAll()")
  @GetMapping(value = "/public/sessions")
  public ResponseEntity<ApiResponse<PageResult<Views.SessionSearchRow>>> listSessions(
      @CurrentRequest RequestContext context, @Valid @ModelAttribute Queries.PublicSessions query) {
    return responses.toResponse(publicEventService.listSessions(context, query));
  }

  @ApiContract(
      id = "PUB-03",
      idempotency = false,
      query = {})
  @PreAuthorize("permitAll()")
  @GetMapping(value = "/public/events/{eventId}")
  public ResponseEntity<ApiResponse<Views.EventPublic>> eventDetail(
      @CurrentRequest RequestContext context, @PathVariable UUID eventId) {
    return responses.toResponse(publicEventService.eventDetail(context, eventId));
  }

  @ApiContract(
      id = "PUB-04",
      idempotency = false,
      query = {})
  @PreAuthorize("permitAll()")
  @GetMapping(value = "/public/events/{eventId}/poster")
  public ResponseEntity<Resource> publicPoster(
      @CurrentRequest RequestContext context, @PathVariable UUID eventId) {
    return responses.download(publicEventService.publicPoster(context, eventId));
  }
}
