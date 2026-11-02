package com.campus.ticketing.service;

import com.campus.ticketing.common.*;
import com.campus.ticketing.dto.requests.Requests;
import com.campus.ticketing.dto.views.Views;
import com.campus.ticketing.enums.*;
import java.util.*;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;

public interface RefundService {
  UseCaseResult<Views.RefundResult> requestRefund(
      RequestContext context, UUID orderId, Requests.Refund request);

  UseCaseResult<Views.MyRefundDetail> refundDetail(
      RequestContext context, UUID orderId, UUID requestId);

  UseCaseResult<Views.RefundResult> retryRefund(
      RequestContext context, UUID orderId, UUID requestId, Requests.Attempt request);

  UseCaseResult<Mutation<Views.RefundRequest>> revokeRequest(
      RequestContext context, UUID orderId, UUID requestId, Requests.Empty request);
}
