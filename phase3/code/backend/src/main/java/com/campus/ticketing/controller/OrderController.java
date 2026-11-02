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
public class OrderController {
  private final ApiResponses responses;
  private final OrderService orderService;

  public OrderController(ApiResponses responses, OrderService orderService) {
    this.responses = responses;
    this.orderService = orderService;
  }

  @ApiContract(
      id = "ME-07",
      idempotency = false,
      query = {"status", "eventId", "page", "pageSize"})
  @PreAuthorize("hasAnyRole('PARTICIPANT')")
  @GetMapping(value = "/me/orders")
  public ResponseEntity<ApiResponse<PageResult<Views.OrderView>>> listMyOrders(
      @CurrentRequest RequestContext context, @Valid @ModelAttribute Queries.Orders query) {
    return responses.toResponse(orderService.listMyOrders(context, query));
  }

  @ApiContract(
      id = "ME-08",
      idempotency = false,
      query = {})
  @PreAuthorize("hasAnyRole('PARTICIPANT')")
  @GetMapping(value = "/me/orders/{orderId}")
  public ResponseEntity<ApiResponse<Views.OrderView>> orderDetail(
      @CurrentRequest RequestContext context, @PathVariable UUID orderId) {
    return responses.toResponse(orderService.orderDetail(context, orderId));
  }

  @ApiContract(
      id = "ME-10",
      idempotency = true,
      query = {})
  @PreAuthorize("hasAnyRole('PARTICIPANT')")
  @PostMapping(value = "/me/orders/{orderId}/cancel")
  public ResponseEntity<ApiResponse<Mutation<Views.OrderView>>> cancel(
      @CurrentRequest RequestContext context,
      @PathVariable UUID orderId,
      @Valid @RequestBody Requests.Cancel request) {
    return responses.toResponse(orderService.cancel(context, orderId, request));
  }

  @ApiContract(
      id = "ME-11",
      idempotency = false,
      query = {})
  @PreAuthorize("hasAnyRole('PARTICIPANT')")
  @GetMapping(value = "/me/orders/{orderId}/queue")
  public ResponseEntity<ApiResponse<Views.QueueView>> queuePosition(
      @CurrentRequest RequestContext context, @PathVariable UUID orderId) {
    return responses.toResponse(orderService.queuePosition(context, orderId));
  }

  @ApiContract(
      id = "ME-12",
      idempotency = true,
      query = {})
  @PreAuthorize("hasAnyRole('PARTICIPANT')")
  @PostMapping(value = "/me/orders/{orderId}/promotion-confirmation")
  public ResponseEntity<ApiResponse<Mutation<Views.OrderView>>> confirmPromotion(
      @CurrentRequest RequestContext context,
      @PathVariable UUID orderId,
      @Valid @RequestBody Requests.Empty request) {
    return responses.toResponse(orderService.confirmPromotion(context, orderId, request));
  }
}
