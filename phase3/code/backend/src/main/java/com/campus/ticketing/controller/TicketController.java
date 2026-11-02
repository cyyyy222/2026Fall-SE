package com.campus.ticketing.controller;

import com.campus.ticketing.common.*;
import com.campus.ticketing.dto.views.Views;
import com.campus.ticketing.enums.*;
import com.campus.ticketing.service.*;
import java.util.*;
import org.springframework.core.io.Resource;
import org.springframework.http.*;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api")
@Validated
public class TicketController {
  private final ApiResponses responses;
  private final TicketService ticketService;

  public TicketController(ApiResponses responses, TicketService ticketService) {
    this.responses = responses;
    this.ticketService = ticketService;
  }

  @ApiContract(
      id = "ME-19",
      idempotency = false,
      query = {})
  @PreAuthorize("hasAnyRole('PARTICIPANT')")
  @GetMapping(value = "/me/tickets/{ticketId}")
  public ResponseEntity<ApiResponse<Views.TicketView>> ticketDetail(
      @CurrentRequest RequestContext context, @PathVariable UUID ticketId) {
    return responses.toResponse(ticketService.ticketDetail(context, ticketId));
  }

  @ApiContract(
      id = "ME-20",
      idempotency = false,
      query = {})
  @PreAuthorize("hasAnyRole('PARTICIPANT')")
  @GetMapping(value = "/me/tickets/{ticketId}/download")
  public ResponseEntity<Resource> downloadText(
      @CurrentRequest RequestContext context, @PathVariable UUID ticketId) {
    return responses.download(ticketService.downloadText(context, ticketId));
  }
}
