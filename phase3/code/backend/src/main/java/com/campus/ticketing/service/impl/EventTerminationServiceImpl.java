package com.campus.ticketing.service.impl;

import com.campus.ticketing.common.*;
import com.campus.ticketing.dto.requests.Requests;
import com.campus.ticketing.dto.views.Views;
import com.campus.ticketing.enums.*;
import com.campus.ticketing.service.EventTerminationService;
import com.campus.ticketing.service.core.*;
import java.util.*;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.bind.annotation.*;

/**
 * Typed application facade; rules and transactions are implemented in the domain use-case service.
 */
@Service
public class EventTerminationServiceImpl implements EventTerminationService {
  private final BusinessSupport support;
  private final DurableWorkflows durableWorkflows;

  public EventTerminationServiceImpl(BusinessSupport support, DurableWorkflows durableWorkflows) {
    this.support = support;
    this.durableWorkflows = durableWorkflows;
  }

  @Override
  public UseCaseResult<Views.TerminationResult> takedown(
      RequestContext context, UUID eventId, Requests.Takedown request) {
    return durableWorkflows.takedown(context, eventId, request);
  }
}
