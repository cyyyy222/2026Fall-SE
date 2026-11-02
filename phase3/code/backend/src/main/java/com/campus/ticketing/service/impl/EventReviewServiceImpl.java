package com.campus.ticketing.service.impl;

import com.campus.ticketing.common.*;
import com.campus.ticketing.dto.queries.Queries;
import com.campus.ticketing.dto.requests.Requests;
import com.campus.ticketing.dto.views.Views;
import com.campus.ticketing.enums.*;
import com.campus.ticketing.service.EventReviewService;
import com.campus.ticketing.service.core.*;
import java.util.*;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.bind.annotation.*;

/**
 * Typed application facade; rules and transactions are implemented in the domain use-case service.
 */
@Service
public class EventReviewServiceImpl implements EventReviewService {
  private final BusinessSupport support;
  private final EventUseCases eventUseCases;

  public EventReviewServiceImpl(BusinessSupport support, EventUseCases eventUseCases) {
    this.support = support;
    this.eventUseCases = eventUseCases;
  }

  @Override
  public UseCaseResult<Views.ReviewQueue> reviewQueue(
      RequestContext context, Queries.Reviews query) {
    return eventUseCases.reviewQueue(context, query);
  }

  @Override
  public UseCaseResult<Views.EventManage> eventDetail(RequestContext context, UUID eventId) {
    return eventUseCases.managementDetail(context, eventId);
  }

  @Override
  public UseCaseResult<Views.ReviewResult> approve(
      RequestContext context, UUID eventId, Requests.Version request) {
    return eventUseCases.approve(context, eventId, request);
  }

  @Override
  public UseCaseResult<Views.ReviewResult> reject(
      RequestContext context, UUID eventId, Requests.VersionReason request) {
    return eventUseCases.reject(context, eventId, request);
  }

  @Override
  public UseCaseResult<PageResult<Views.EventManage>> listEvents(
      RequestContext context, Queries.AdminEvents query) {
    return eventUseCases.listAdminEvents(context, query);
  }
}
