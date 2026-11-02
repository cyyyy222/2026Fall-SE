package com.campus.ticketing.service.impl;

import com.campus.ticketing.common.*;
import com.campus.ticketing.dto.queries.Queries;
import com.campus.ticketing.dto.requests.Requests;
import com.campus.ticketing.dto.views.Views;
import com.campus.ticketing.enums.*;
import com.campus.ticketing.service.FinanceAuditService;
import com.campus.ticketing.service.core.*;
import java.util.*;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.bind.annotation.*;

/**
 * Typed application facade; rules and transactions are implemented in the domain use-case service.
 */
@Service
public class FinanceAuditServiceImpl implements FinanceAuditService {
  private final BusinessSupport support;
  private final ReportingUseCases reportingUseCases;

  public FinanceAuditServiceImpl(BusinessSupport support, ReportingUseCases reportingUseCases) {
    this.support = support;
    this.reportingUseCases = reportingUseCases;
  }

  @Override
  public UseCaseResult<PageResult<Views.PaymentAttempt>> listPayments(
      RequestContext context, Queries.Payments query) {
    return reportingUseCases.listPayments(context, query);
  }

  @Override
  public UseCaseResult<Views.AdminRefundDetail> refundRequestDetail(
      RequestContext context, UUID requestId) {
    return reportingUseCases.refundRequestDetail(context, requestId);
  }

  @Override
  public UseCaseResult<Views.RefundResult> retryAuthorizedRefund(
      RequestContext context, UUID requestId, Requests.Refund request) {
    return reportingUseCases.retryAuthorizedRefund(context, requestId, request);
  }

  @Override
  public UseCaseResult<PageResult<Views.RefundRequest>> listRefundRequests(
      RequestContext context, Queries.Refunds query) {
    return reportingUseCases.listRefundRequests(context, query);
  }
}
