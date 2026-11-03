package com.campus.ticketing.service.impl;

import com.campus.ticketing.common.*;
import com.campus.ticketing.dto.queries.Queries;
import com.campus.ticketing.dto.requests.Requests;
import com.campus.ticketing.dto.views.Views;
import com.campus.ticketing.service.AppealService;
import com.campus.ticketing.service.core.GovernanceUseCases;
import java.util.*;
import org.springframework.stereotype.Service;

@Service
public class AppealServiceImpl implements AppealService {
  private final GovernanceUseCases governance;

  public AppealServiceImpl(GovernanceUseCases governance) {
    this.governance = governance;
  }

  @Override
  public UseCaseResult<List<Views.RestrictionView>> myRestrictions(RequestContext context) {
    return governance.myRestrictions(context);
  }

  @Override
  public UseCaseResult<List<Views.RestrictionAppealView>> myAppeals(RequestContext context) {
    return governance.myAppeals(context);
  }

  @Override
  public UseCaseResult<Views.RestrictionAppealView> createAppeal(
      RequestContext context, Requests.RestrictionAppeal request) {
    return governance.createAppeal(context, request);
  }

  @Override
  public UseCaseResult<PageResult<Views.RestrictionAppealView>> listAppeals(
      RequestContext context, Queries.RestrictionAppeals query) {
    return governance.listAppeals(context, query);
  }

  @Override
  public UseCaseResult<Views.RestrictionAppealView> resolveAppeal(
      RequestContext context, UUID appealId, Requests.ResolveAppeal request) {
    return governance.resolveAppeal(context, appealId, request);
  }
}
