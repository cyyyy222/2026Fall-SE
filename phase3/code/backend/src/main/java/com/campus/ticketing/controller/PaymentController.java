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
public class PaymentController {
  private final ApiResponses responses;
  private final PaymentService paymentService;

  public PaymentController(ApiResponses responses, PaymentService paymentService) {
    this.responses = responses;
    this.paymentService = paymentService;
  }

  @ApiContract(
      id = "ME-13",
      idempotency = false,
      query = {})
  @PreAuthorize("hasAnyRole('PARTICIPANT')")
  @PostMapping(value = "/me/orders/{orderId}/payment-attempts")
  public ResponseEntity<ApiResponse<Views.PaymentResult>> pay(
      @CurrentRequest RequestContext context,
      @PathVariable UUID orderId,
      @Valid @RequestBody Requests.Attempt request) {
    return responses.toResponse(paymentService.pay(context, orderId, request));
  }

  @ApiContract(
      id = "ME-14",
      idempotency = false,
      query = {})
  @PreAuthorize("hasAnyRole('PARTICIPANT')")
  @GetMapping(value = "/me/orders/{orderId}/payments")
  public ResponseEntity<ApiResponse<Views.PaymentAudit>> paymentHistory(
      @CurrentRequest RequestContext context, @PathVariable UUID orderId) {
    return responses.toResponse(paymentService.paymentHistory(context, orderId));
  }
}
