package com.campus.ticketing.service.impl;

import com.campus.ticketing.common.*;
import com.campus.ticketing.dto.requests.Requests;
import com.campus.ticketing.dto.views.Views;
import com.campus.ticketing.enums.*;
import com.campus.ticketing.service.RefundService;
import com.campus.ticketing.service.core.*;
import java.util.*;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.bind.annotation.*;

/**
 * Typed application facade; rules and transactions are implemented in the domain use-case service.
 */
@Service
public class RefundServiceImpl implements RefundService {
  private final BusinessSupport support;
  private final OrderUseCases orderUseCases;

  public RefundServiceImpl(BusinessSupport support, OrderUseCases orderUseCases) {
    this.support = support;
    this.orderUseCases = orderUseCases;
  }

  @Override
  public UseCaseResult<Views.RefundResult> requestRefund(
      RequestContext context, UUID orderId, Requests.Refund request) {
    return orderUseCases.requestRefund(context, orderId, request);
  }

  @Override
  public UseCaseResult<Views.MyRefundDetail> refundDetail(
      RequestContext context, UUID orderId, UUID requestId) {
    return orderUseCases.refundDetail(context, orderId, requestId);
  }

  @Override
  public UseCaseResult<Views.RefundResult> retryRefund(
      RequestContext context, UUID orderId, UUID requestId, Requests.Attempt request) {
    return orderUseCases.retryRefund(context, orderId, requestId, request);
  }

  @Override
  public UseCaseResult<Mutation<Views.RefundRequest>> revokeRequest(
      RequestContext context, UUID orderId, UUID requestId, Requests.Empty request) {
    return orderUseCases.revokeRequest(context, orderId, requestId, request);
  }
}
