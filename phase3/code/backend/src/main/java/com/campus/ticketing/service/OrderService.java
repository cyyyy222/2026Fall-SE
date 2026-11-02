package com.campus.ticketing.service;

import com.campus.ticketing.common.*;
import com.campus.ticketing.dto.queries.Queries;
import com.campus.ticketing.dto.requests.Requests;
import com.campus.ticketing.dto.views.Views;
import com.campus.ticketing.enums.*;
import java.util.*;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;

public interface OrderService {
  UseCaseResult<PageResult<Views.OrderView>> listMyOrders(
      RequestContext context, Queries.Orders query);

  UseCaseResult<Views.OrderView> orderDetail(RequestContext context, UUID orderId);

  UseCaseResult<Mutation<Views.OrderView>> cancel(
      RequestContext context, UUID orderId, Requests.Cancel request);

  UseCaseResult<Views.QueueView> queuePosition(RequestContext context, UUID orderId);

  UseCaseResult<Mutation<Views.OrderView>> confirmPromotion(
      RequestContext context, UUID orderId, Requests.Empty request);
}
