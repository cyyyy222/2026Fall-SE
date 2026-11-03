package com.campus.ticketing.service;

import com.campus.ticketing.common.*;
import com.campus.ticketing.dto.queries.Queries;
import com.campus.ticketing.dto.requests.Requests;
import com.campus.ticketing.dto.views.Views;
import java.util.*;

public interface AppealService {
  UseCaseResult<List<Views.RestrictionView>> myRestrictions(RequestContext context);

  UseCaseResult<List<Views.RestrictionAppealView>> myAppeals(RequestContext context);

  UseCaseResult<Views.RestrictionAppealView> createAppeal(
      RequestContext context, Requests.RestrictionAppeal request);

  UseCaseResult<PageResult<Views.RestrictionAppealView>> listAppeals(
      RequestContext context, Queries.RestrictionAppeals query);

  UseCaseResult<Views.RestrictionAppealView> resolveAppeal(
      RequestContext context, UUID appealId, Requests.ResolveAppeal request);
}
