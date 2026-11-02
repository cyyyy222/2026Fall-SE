package com.campus.ticketing.service.impl;

import com.campus.ticketing.common.*;
import com.campus.ticketing.dto.queries.Queries;
import com.campus.ticketing.dto.views.Views;
import com.campus.ticketing.enums.*;
import com.campus.ticketing.service.PublicEventService;
import com.campus.ticketing.service.core.*;
import java.util.*;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.bind.annotation.*;

/**
 * Typed application facade; rules and transactions are implemented in the domain use-case service.
 */
@Service
public class PublicEventServiceImpl implements PublicEventService {
  private final BusinessSupport support;
  private final EventUseCases eventUseCases;
  private final FileUseCases fileUseCases;

  public PublicEventServiceImpl(
      BusinessSupport support, EventUseCases eventUseCases, FileUseCases fileUseCases) {
    this.support = support;
    this.eventUseCases = eventUseCases;
    this.fileUseCases = fileUseCases;
  }

  @Override
  public UseCaseResult<PageResult<Views.SessionSearchRow>> listSessions(
      RequestContext context, Queries.PublicSessions query) {
    return eventUseCases.listSessions(context, query);
  }

  @Override
  public UseCaseResult<Views.EventPublic> eventDetail(RequestContext context, UUID eventId) {
    return eventUseCases.eventDetail(context, eventId);
  }

  @Override
  public DownloadResult publicPoster(RequestContext context, UUID eventId) {
    return fileUseCases.publicPoster(context, eventId);
  }
}
