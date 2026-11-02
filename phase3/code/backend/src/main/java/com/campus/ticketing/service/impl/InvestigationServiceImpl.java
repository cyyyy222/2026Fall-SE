package com.campus.ticketing.service.impl;

import com.campus.ticketing.common.*;
import com.campus.ticketing.dto.queries.Queries;
import com.campus.ticketing.dto.requests.Requests;
import com.campus.ticketing.dto.views.Views;
import com.campus.ticketing.enums.*;
import com.campus.ticketing.service.InvestigationService;
import com.campus.ticketing.service.core.*;
import java.util.*;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.bind.annotation.*;

/**
 * Typed application facade; rules and transactions are implemented in the domain use-case service.
 */
@Service
public class InvestigationServiceImpl implements InvestigationService {
  private final BusinessSupport support;
  private final GovernanceUseCases governanceUseCases;

  public InvestigationServiceImpl(BusinessSupport support, GovernanceUseCases governanceUseCases) {
    this.support = support;
    this.governanceUseCases = governanceUseCases;
  }

  @Override
  public UseCaseResult<PageResult<Views.ReportAdminRow>> listReports(
      RequestContext context, Queries.AdminReports query) {
    return governanceUseCases.listReports(context, query);
  }

  @Override
  public UseCaseResult<Views.InvestigationResult> investigate(
      RequestContext context, Requests.Investigation request) {
    return governanceUseCases.investigate(context, request);
  }

  @Override
  public UseCaseResult<Views.InvestigationDetail> investigationDetail(
      RequestContext context, UUID investigationId) {
    return governanceUseCases.investigationDetail(context, investigationId);
  }

  @Override
  public UseCaseResult<PageResult<Views.InvestigationResult>> listInvestigations(
      RequestContext context, Queries.Investigations query) {
    return governanceUseCases.listInvestigations(context, query);
  }
}
