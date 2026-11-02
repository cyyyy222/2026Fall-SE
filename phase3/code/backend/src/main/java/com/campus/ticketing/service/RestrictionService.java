package com.campus.ticketing.service;

import com.campus.ticketing.common.*;
import com.campus.ticketing.dto.queries.Queries;
import com.campus.ticketing.dto.requests.Requests;
import com.campus.ticketing.dto.views.Views;
import com.campus.ticketing.enums.*;
import java.util.*;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;

public interface RestrictionService {
  UseCaseResult<PageResult<Views.RestrictionCandidate>> candidates(
      RequestContext context, Queries.Candidates query);

  UseCaseResult<Views.RestrictionView> restrictRegistration(
      RequestContext context, Requests.Restriction request);

  UseCaseResult<Views.RestrictionEvidence> candidateEvidence(
      RequestContext context, UUID phoneIdentityId, Queries.Candidate query);

  void reconcileDue(UUID jobId);
}
