package com.campus.ticketing.service.impl;

import com.campus.ticketing.common.*;
import com.campus.ticketing.dto.queries.Queries;
import com.campus.ticketing.dto.requests.Requests;
import com.campus.ticketing.dto.views.Views;
import com.campus.ticketing.enums.*;
import com.campus.ticketing.service.EventService;
import com.campus.ticketing.service.core.*;
import java.util.*;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.bind.annotation.*;

/**
 * Typed application facade; rules and transactions are implemented in the domain use-case service.
 */
@Service
public class EventServiceImpl implements EventService {
  private final BusinessSupport support;
  private final DurableWorkflows durableWorkflows;
  private final EventUseCases eventUseCases;
  private final FileUseCases fileUseCases;

  public EventServiceImpl(
      BusinessSupport support,
      DurableWorkflows durableWorkflows,
      EventUseCases eventUseCases,
      FileUseCases fileUseCases) {
    this.support = support;
    this.durableWorkflows = durableWorkflows;
    this.eventUseCases = eventUseCases;
    this.fileUseCases = fileUseCases;
  }

  @Override
  public UseCaseResult<PageResult<Views.EventManage>> listMine(
      RequestContext context, Queries.Events query) {
    return eventUseCases.listMine(context, query);
  }

  @Override
  public UseCaseResult<Views.EventManage> createDraft(
      RequestContext context, Requests.Empty request) {
    return eventUseCases.createDraft(context, request);
  }

  @Override
  public UseCaseResult<Views.EventManage> managementDetail(RequestContext context, UUID eventId) {
    return eventUseCases.managementDetail(context, eventId);
  }

  @Override
  public UseCaseResult<Mutation<Views.EventManage>> saveDraft(
      RequestContext context, UUID eventId, Requests.Draft request) {
    return eventUseCases.saveDraft(context, eventId, request);
  }

  @Override
  public UseCaseResult<Mutation<Views.EventManage>> replacePoster(
      RequestContext context, UUID eventId, Requests.PosterUpload request) {
    return fileUseCases.replacePoster(context, eventId, request);
  }

  @Override
  public UseCaseResult<Mutation<Views.EventManage>> removePoster(
      RequestContext context, UUID eventId, Queries.Version query) {
    return fileUseCases.removePoster(context, eventId, query);
  }

  @Override
  public UseCaseResult<Mutation<Views.EventManage>> submitReview(
      RequestContext context, UUID eventId, Requests.Version request) {
    return eventUseCases.submitReview(context, eventId, request);
  }

  @Override
  public UseCaseResult<Mutation<Views.EventManage>> withdrawReview(
      RequestContext context, UUID eventId, Requests.Version request) {
    return eventUseCases.withdrawReview(context, eventId, request);
  }

  @Override
  public UseCaseResult<Mutation<Views.EventManage>> updatePublishedSettings(
      RequestContext context, UUID eventId, Requests.PublishedEdit request) {
    return eventUseCases.updatePublishedSettings(context, eventId, request);
  }

  @Override
  public UseCaseResult<Views.TerminationResult> cancelEvent(
      RequestContext context, UUID eventId, Requests.VersionReason request) {
    return durableWorkflows.cancelEvent(context, eventId, request);
  }

  @Override
  public UseCaseResult<Views.SettlementProgress> settlementProgress(
      RequestContext context, UUID eventId) {
    return durableWorkflows.settlementProgress(context, eventId);
  }
}
