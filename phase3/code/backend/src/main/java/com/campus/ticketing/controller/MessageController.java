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
public class MessageController {
  private final ApiResponses responses;
  private final MessageService messageService;

  public MessageController(ApiResponses responses, MessageService messageService) {
    this.responses = responses;
    this.messageService = messageService;
  }

  @ApiContract(
      id = "ME-24",
      idempotency = false,
      query = {"unreadOnly", "page", "pageSize"})
  @PreAuthorize("isAuthenticated()")
  @GetMapping(value = "/me/messages")
  public ResponseEntity<ApiResponse<PageResult<Views.MessageView>>> listMine(
      @CurrentRequest RequestContext context, @Valid @ModelAttribute Queries.Messages query) {
    return responses.toResponse(messageService.listMine(context, query));
  }

  @ApiContract(
      id = "ME-25",
      idempotency = false,
      query = {})
  @PreAuthorize("isAuthenticated()")
  @GetMapping(value = "/me/messages/{messageId}")
  public ResponseEntity<ApiResponse<Views.MessageView>> messageDetail(
      @CurrentRequest RequestContext context, @PathVariable UUID messageId) {
    return responses.toResponse(messageService.messageDetail(context, messageId));
  }

  @ApiContract(
      id = "ME-26",
      idempotency = true,
      query = {})
  @PreAuthorize("isAuthenticated()")
  @PostMapping(value = "/me/messages/{messageId}/read")
  public ResponseEntity<ApiResponse<Mutation<Views.MessageView>>> markRead(
      @CurrentRequest RequestContext context,
      @PathVariable UUID messageId,
      @Valid @RequestBody Requests.Empty request) {
    return responses.toResponse(messageService.markRead(context, messageId, request));
  }
}
