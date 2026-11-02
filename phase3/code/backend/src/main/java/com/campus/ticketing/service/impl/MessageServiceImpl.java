package com.campus.ticketing.service.impl;

import com.campus.ticketing.common.*;
import com.campus.ticketing.dto.queries.Queries;
import com.campus.ticketing.dto.requests.Requests;
import com.campus.ticketing.dto.views.Views;
import com.campus.ticketing.enums.*;
import com.campus.ticketing.service.MessageService;
import com.campus.ticketing.service.core.*;
import java.util.*;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.bind.annotation.*;

/**
 * Typed application facade; rules and transactions are implemented in the domain use-case service.
 */
@Service
public class MessageServiceImpl implements MessageService {
  private final BusinessSupport support;
  private final GovernanceUseCases governanceUseCases;

  public MessageServiceImpl(BusinessSupport support, GovernanceUseCases governanceUseCases) {
    this.support = support;
    this.governanceUseCases = governanceUseCases;
  }

  @Override
  public UseCaseResult<PageResult<Views.MessageView>> listMine(
      RequestContext context, Queries.Messages query) {
    return governanceUseCases.listMessages(context, query);
  }

  @Override
  public UseCaseResult<Views.MessageView> messageDetail(RequestContext context, UUID messageId) {
    return governanceUseCases.messageDetail(context, messageId);
  }

  @Override
  public UseCaseResult<Mutation<Views.MessageView>> markRead(
      RequestContext context, UUID messageId, Requests.Empty request) {
    return governanceUseCases.markRead(context, messageId, request);
  }
}
