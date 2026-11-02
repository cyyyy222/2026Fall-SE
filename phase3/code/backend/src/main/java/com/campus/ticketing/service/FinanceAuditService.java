package com.campus.ticketing.service;

import com.campus.ticketing.common.*;
import com.campus.ticketing.dto.queries.Queries;
import com.campus.ticketing.dto.requests.Requests;
import com.campus.ticketing.dto.views.Views;
import com.campus.ticketing.enums.*;
import java.util.*;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;

public interface FinanceAuditService {
  UseCaseResult<PageResult<Views.PaymentAttempt>> listPayments(
      RequestContext context, Queries.Payments query);

  UseCaseResult<Views.AdminRefundDetail> refundRequestDetail(
      RequestContext context, UUID requestId);

  UseCaseResult<Views.RefundResult> retryAuthorizedRefund(
      RequestContext context, UUID requestId, Requests.Refund request);

  UseCaseResult<PageResult<Views.RefundRequest>> listRefundRequests(
      RequestContext context, Queries.Refunds query);
}
