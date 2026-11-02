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
public class AdminFinanceController {
  private final ApiResponses responses;
  private final FinanceAuditService financeAuditService;

  public AdminFinanceController(ApiResponses responses, FinanceAuditService financeAuditService) {
    this.responses = responses;
    this.financeAuditService = financeAuditService;
  }

  @ApiContract(
      id = "ADM-16",
      idempotency = false,
      query = {"orderId", "result", "from", "to", "page", "pageSize"})
  @PreAuthorize("hasAnyRole('ADMIN')")
  @GetMapping(value = "/admin/payments")
  public ResponseEntity<ApiResponse<PageResult<Views.PaymentAttempt>>> listPayments(
      @CurrentRequest RequestContext context, @Valid @ModelAttribute Queries.Payments query) {
    return responses.toResponse(financeAuditService.listPayments(context, query));
  }

  @ApiContract(
      id = "ADM-17",
      idempotency = false,
      query = {})
  @PreAuthorize("hasAnyRole('ADMIN')")
  @GetMapping(value = "/admin/refund-requests/{requestId}")
  public ResponseEntity<ApiResponse<Views.AdminRefundDetail>> refundRequestDetail(
      @CurrentRequest RequestContext context, @PathVariable UUID requestId) {
    return responses.toResponse(financeAuditService.refundRequestDetail(context, requestId));
  }

  @ApiContract(
      id = "ADM-18",
      idempotency = true,
      query = {})
  @PreAuthorize("hasAnyRole('ADMIN')")
  @PostMapping(value = "/admin/refund-requests/{requestId}/attempts")
  public ResponseEntity<ApiResponse<Views.RefundResult>> retryAuthorizedRefund(
      @CurrentRequest RequestContext context,
      @PathVariable UUID requestId,
      @Valid @RequestBody Requests.Refund request) {
    return responses.toResponse(
        financeAuditService.retryAuthorizedRefund(context, requestId, request));
  }

  @ApiContract(
      id = "ADM-31",
      idempotency = false,
      query = {"status", "source", "orderId", "page", "pageSize"})
  @PreAuthorize("hasAnyRole('ADMIN')")
  @GetMapping(value = "/admin/refund-requests")
  public ResponseEntity<ApiResponse<PageResult<Views.RefundRequest>>> listRefundRequests(
      @CurrentRequest RequestContext context, @Valid @ModelAttribute Queries.Refunds query) {
    return responses.toResponse(financeAuditService.listRefundRequests(context, query));
  }
}
