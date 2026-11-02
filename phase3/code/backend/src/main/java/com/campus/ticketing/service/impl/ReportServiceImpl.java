package com.campus.ticketing.service.impl;

import com.campus.ticketing.common.*;
import com.campus.ticketing.dto.queries.Queries;
import com.campus.ticketing.dto.requests.Requests;
import com.campus.ticketing.dto.views.Views;
import com.campus.ticketing.enums.*;
import com.campus.ticketing.service.ReportService;
import com.campus.ticketing.service.core.*;
import java.util.*;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.bind.annotation.*;

/**
 * Typed application facade; rules and transactions are implemented in the domain use-case service.
 */
@Service
public class ReportServiceImpl implements ReportService {
  private final BusinessSupport support;
  private final GovernanceUseCases governanceUseCases;

  public ReportServiceImpl(BusinessSupport support, GovernanceUseCases governanceUseCases) {
    this.support = support;
    this.governanceUseCases = governanceUseCases;
  }

  @Override
  public UseCaseResult<Views.ReportView> createReport(
      RequestContext context, Requests.Report request) {
    return governanceUseCases.createReport(context, request);
  }

  @Override
  public UseCaseResult<PageResult<Views.ReportView>> listMine(
      RequestContext context, Queries.Reports query) {
    return governanceUseCases.listMyReports(context, query);
  }

  @Override
  public UseCaseResult<Views.ReportView> reportDetail(RequestContext context, UUID reportId) {
    return governanceUseCases.reportDetail(context, reportId);
  }
}
