package com.campus.ticketing.service.impl;

import com.campus.ticketing.common.*;
import com.campus.ticketing.dto.requests.Requests;
import com.campus.ticketing.dto.views.Views;
import com.campus.ticketing.enums.*;
import com.campus.ticketing.service.PaymentService;
import com.campus.ticketing.service.core.*;
import java.util.*;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.bind.annotation.*;

/**
 * Typed application facade; rules and transactions are implemented in the domain use-case service.
 */
@Service
public class PaymentServiceImpl implements PaymentService {
  private final BusinessSupport support;
  private final OrderUseCases orderUseCases;

  public PaymentServiceImpl(BusinessSupport support, OrderUseCases orderUseCases) {
    this.support = support;
    this.orderUseCases = orderUseCases;
  }

  @Override
  public UseCaseResult<Views.PaymentResult> pay(
      RequestContext context, UUID orderId, Requests.Attempt request) {
    return orderUseCases.pay(context, orderId, request);
  }

  @Override
  public UseCaseResult<Views.PaymentAudit> paymentHistory(RequestContext context, UUID orderId) {
    return orderUseCases.paymentHistory(context, orderId);
  }
}
