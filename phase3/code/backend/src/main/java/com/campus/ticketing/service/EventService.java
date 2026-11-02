package com.campus.ticketing.service;

import com.campus.ticketing.common.*;
import com.campus.ticketing.dto.queries.Queries;
import com.campus.ticketing.dto.requests.Requests;
import com.campus.ticketing.dto.views.Views;
import com.campus.ticketing.enums.*;
import java.util.*;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;

public interface EventService {
  UseCaseResult<PageResult<Views.EventManage>> listMine(
      RequestContext context, Queries.Events query);

  UseCaseResult<Views.EventManage> createDraft(RequestContext context, Requests.Empty request);

  UseCaseResult<Views.EventManage> managementDetail(RequestContext context, UUID eventId);

  UseCaseResult<Mutation<Views.EventManage>> saveDraft(
      RequestContext context, UUID eventId, Requests.Draft request);

  UseCaseResult<Mutation<Views.EventManage>> replacePoster(
      RequestContext context, UUID eventId, Requests.PosterUpload request);

  UseCaseResult<Mutation<Views.EventManage>> removePoster(
      RequestContext context, UUID eventId, Queries.Version query);

  UseCaseResult<Mutation<Views.EventManage>> submitReview(
      RequestContext context, UUID eventId, Requests.Version request);

  UseCaseResult<Mutation<Views.EventManage>> withdrawReview(
      RequestContext context, UUID eventId, Requests.Version request);

  UseCaseResult<Mutation<Views.EventManage>> updatePublishedSettings(
      RequestContext context, UUID eventId, Requests.PublishedEdit request);

  UseCaseResult<Views.TerminationResult> cancelEvent(
      RequestContext context, UUID eventId, Requests.VersionReason request);

  UseCaseResult<Views.SettlementProgress> settlementProgress(RequestContext context, UUID eventId);
}
