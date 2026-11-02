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
public class RefundController {
  private final ApiResponses responses;
  private final RefundService refundService;

  public RefundController(ApiResponses responses, RefundService refundService) {
    this.responses = responses;
    this.refundService = refundService;
  }

  @ApiContract(
      id = "ME-15",
      idempotency = true,
      query = {})
  @PreAuthorize("hasAnyRole('PARTICIPANT')")
  @PostMapping(value = "/me/orders/{orderId}/refund-requests")
  public ResponseEntity<ApiResponse<Views.RefundResult>> requestRefund(
      @CurrentRequest RequestContext context,
      @PathVariable UUID orderId,
      @Valid @RequestBody Requests.Refund request) {
    return responses.toResponse(refundService.requestRefund(context, orderId, request));
  }

  @ApiContract(
      id = "ME-16",
      idempotency = false,
      query = {})
  @PreAuthorize("hasAnyRole('PARTICIPANT')")
  @GetMapping(value = "/me/orders/{orderId}/refund-requests/{requestId}")
  public ResponseEntity<ApiResponse<Views.MyRefundDetail>> refundDetail(
      @CurrentRequest RequestContext context,
      @PathVariable UUID orderId,
      @PathVariable UUID requestId) {
    return responses.toResponse(refundService.refundDetail(context, orderId, requestId));
  }

  @ApiContract(
      id = "ME-17",
      idempotency = true,
      query = {})
  @PreAuthorize("hasAnyRole('PARTICIPANT')")
  @PostMapping(value = "/me/orders/{orderId}/refund-requests/{requestId}/attempts")
  public ResponseEntity<ApiResponse<Views.RefundResult>> retryRefund(
      @CurrentRequest RequestContext context,
      @PathVariable UUID orderId,
      @PathVariable UUID requestId,
      @Valid @RequestBody Requests.Attempt request) {
    return responses.toResponse(refundService.retryRefund(context, orderId, requestId, request));
  }

  @ApiContract(
      id = "ME-18",
      idempotency = true,
      query = {})
  @PreAuthorize("hasAnyRole('PARTICIPANT')")
  @PostMapping(value = "/me/orders/{orderId}/refund-requests/{requestId}/revocation")
  public ResponseEntity<ApiResponse<Mutation<Views.RefundRequest>>> revokeRequest(
      @CurrentRequest RequestContext context,
      @PathVariable UUID orderId,
      @PathVariable UUID requestId,
      @Valid @RequestBody Requests.Empty request) {
    return responses.toResponse(refundService.revokeRequest(context, orderId, requestId, request));
  }
}
