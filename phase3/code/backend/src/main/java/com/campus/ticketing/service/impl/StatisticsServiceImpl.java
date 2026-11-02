package com.campus.ticketing.service.impl;

import com.campus.ticketing.common.*;
import com.campus.ticketing.dto.queries.Queries;
import com.campus.ticketing.dto.views.Views;
import com.campus.ticketing.enums.*;
import com.campus.ticketing.service.StatisticsService;
import com.campus.ticketing.service.core.*;
import java.util.*;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.bind.annotation.*;

/**
 * Typed application facade; rules and transactions are implemented in the domain use-case service.
 */
@Service
public class StatisticsServiceImpl implements StatisticsService {
  private final BusinessSupport support;
  private final ReportingUseCases reportingUseCases;

  public StatisticsServiceImpl(BusinessSupport support, ReportingUseCases reportingUseCases) {
    this.support = support;
    this.reportingUseCases = reportingUseCases;
  }

  @Override
  public UseCaseResult<Views.Counts> liveCounts(
      RequestContext context, UUID eventId, Queries.Session query) {
    return reportingUseCases.liveCounts(context, eventId, query);
  }

  @Override
  public UseCaseResult<Views.StatisticsProjection> statistics(
      RequestContext context, UUID eventId, Queries.Session query) {
    return reportingUseCases.statistics(context, eventId, query);
  }

  @Override
  public UseCaseResult<Views.PaymentSummary> paymentSummary(
      RequestContext context, UUID eventId, Queries.Session query) {
    return reportingUseCases.paymentSummary(context, eventId, query);
  }
}
