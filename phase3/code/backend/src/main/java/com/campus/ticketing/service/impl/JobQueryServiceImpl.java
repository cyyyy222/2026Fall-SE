package com.campus.ticketing.service.impl;

import com.campus.ticketing.common.*;
import com.campus.ticketing.dto.queries.Queries;
import com.campus.ticketing.dto.views.Views;
import com.campus.ticketing.enums.*;
import com.campus.ticketing.service.JobQueryService;
import com.campus.ticketing.service.core.*;
import java.util.*;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.bind.annotation.*;

/**
 * Typed application facade; rules and transactions are implemented in the domain use-case service.
 */
@Service
public class JobQueryServiceImpl implements JobQueryService {
  private final BusinessSupport support;
  private final DurableWorkflows durableWorkflows;

  public JobQueryServiceImpl(BusinessSupport support, DurableWorkflows durableWorkflows) {
    this.support = support;
    this.durableWorkflows = durableWorkflows;
  }

  @Override
  public UseCaseResult<PageResult<Views.JobView>> listJobs(
      RequestContext context, Queries.Jobs query) {
    return durableWorkflows.listJobs(context, query);
  }

  @Override
  public UseCaseResult<Views.JobView> jobDetail(RequestContext context, UUID jobId) {
    return durableWorkflows.jobDetail(context, jobId);
  }
}
