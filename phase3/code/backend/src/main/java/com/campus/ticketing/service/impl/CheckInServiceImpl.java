package com.campus.ticketing.service.impl;

import com.campus.ticketing.common.*;
import com.campus.ticketing.dto.requests.Requests;
import com.campus.ticketing.dto.views.Views;
import com.campus.ticketing.enums.*;
import com.campus.ticketing.service.CheckInService;
import com.campus.ticketing.service.core.*;
import java.util.*;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.bind.annotation.*;

/**
 * Typed application facade; rules and transactions are implemented in the domain use-case service.
 */
@Service
public class CheckInServiceImpl implements CheckInService {
  private final BusinessSupport support;
  private final CheckInUseCases checkInUseCases;
  private final DurableWorkflows durableWorkflows;

  public CheckInServiceImpl(
      BusinessSupport support, CheckInUseCases checkInUseCases, DurableWorkflows durableWorkflows) {
    this.support = support;
    this.checkInUseCases = checkInUseCases;
    this.durableWorkflows = durableWorkflows;
  }

  @Override
  public UseCaseResult<Views.CheckerContext> context(RequestContext context) {
    return checkInUseCases.context(context);
  }

  @Override
  public UseCaseResult<Views.TicketPreview> previewTicket(
      RequestContext context, Requests.Ticket request) {
    return checkInUseCases.previewTicket(context, request);
  }

  @Override
  public UseCaseResult<Views.CheckInResult> confirmCheckIn(
      RequestContext context, Requests.Ticket request) {
    return checkInUseCases.confirmCheckIn(context, request);
  }

  @Override
  public UseCaseResult<Views.Reconciliation> reconcileAbsence(
      RequestContext context, UUID sessionId, Requests.Empty request) {
    return durableWorkflows.reconcileAbsence(context, sessionId, request);
  }
}
