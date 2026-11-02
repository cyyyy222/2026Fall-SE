package com.campus.ticketing.service.impl;

import com.campus.ticketing.common.*;
import com.campus.ticketing.dto.queries.Queries;
import com.campus.ticketing.dto.requests.Requests;
import com.campus.ticketing.dto.views.Views;
import com.campus.ticketing.enums.*;
import com.campus.ticketing.service.OrderService;
import com.campus.ticketing.service.core.*;
import java.util.*;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.bind.annotation.*;

/**
 * Typed application facade; rules and transactions are implemented in the domain use-case service.
 */
@Service
public class OrderServiceImpl implements OrderService {
  private final BusinessSupport support;
  private final OrderUseCases orderUseCases;

  public OrderServiceImpl(BusinessSupport support, OrderUseCases orderUseCases) {
    this.support = support;
    this.orderUseCases = orderUseCases;
  }

  @Override
  public UseCaseResult<PageResult<Views.OrderView>> listMyOrders(
      RequestContext context, Queries.Orders query) {
    return orderUseCases.listMyOrders(context, query);
  }

  @Override
  public UseCaseResult<Views.OrderView> orderDetail(RequestContext context, UUID orderId) {
    return orderUseCases.orderDetail(context, orderId);
  }

  @Override
  public UseCaseResult<Mutation<Views.OrderView>> cancel(
      RequestContext context, UUID orderId, Requests.Cancel request) {
    return orderUseCases.cancel(context, orderId, request);
  }

  @Override
  public UseCaseResult<Views.QueueView> queuePosition(RequestContext context, UUID orderId) {
    return orderUseCases.queuePosition(context, orderId);
  }

  @Override
  public UseCaseResult<Mutation<Views.OrderView>> confirmPromotion(
      RequestContext context, UUID orderId, Requests.Empty request) {
    return orderUseCases.confirmPromotion(context, orderId, request);
  }
}
