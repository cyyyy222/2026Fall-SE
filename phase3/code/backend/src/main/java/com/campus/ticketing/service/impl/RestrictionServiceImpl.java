package com.campus.ticketing.service.impl;

import com.campus.ticketing.common.*;
import com.campus.ticketing.dto.queries.Queries;
import com.campus.ticketing.dto.requests.Requests;
import com.campus.ticketing.dto.views.Views;
import com.campus.ticketing.enums.*;
import com.campus.ticketing.service.RestrictionService;
import com.campus.ticketing.service.core.*;
import java.util.*;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.bind.annotation.*;

/**
 * Typed application facade; rules and transactions are implemented in the domain use-case service.
 */
@Service
public class RestrictionServiceImpl implements RestrictionService {
  private final BusinessSupport support;
  private final GovernanceUseCases governanceUseCases;

  public RestrictionServiceImpl(BusinessSupport support, GovernanceUseCases governanceUseCases) {
    this.support = support;
    this.governanceUseCases = governanceUseCases;
  }

  @Override
  public UseCaseResult<PageResult<Views.RestrictionCandidate>> candidates(
      RequestContext context, Queries.Candidates query) {
    return governanceUseCases.candidates(context, query);
  }

  @Override
  public UseCaseResult<Views.RestrictionView> restrictRegistration(
      RequestContext context, Requests.Restriction request) {
    return governanceUseCases.restrictRegistration(context, request);
  }

  @Override
  public UseCaseResult<Views.RestrictionEvidence> candidateEvidence(
      RequestContext context, UUID phoneIdentityId, Queries.Candidate query) {
    return governanceUseCases.candidateEvidence(context, phoneIdentityId, query);
  }

  @Override
  public void reconcileDue(UUID jobId) {
    governanceUseCases.reconcileDue(jobId);
  }
}
