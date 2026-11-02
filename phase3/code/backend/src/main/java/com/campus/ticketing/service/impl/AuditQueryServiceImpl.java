package com.campus.ticketing.service.impl;

import com.campus.ticketing.common.*;
import com.campus.ticketing.dto.queries.Queries;
import com.campus.ticketing.dto.views.Views;
import com.campus.ticketing.enums.*;
import com.campus.ticketing.service.AuditQueryService;
import com.campus.ticketing.service.core.*;
import java.util.*;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.bind.annotation.*;

/**
 * Typed application facade; rules and transactions are implemented in the domain use-case service.
 */
@Service
public class AuditQueryServiceImpl implements AuditQueryService {
  private final BusinessSupport support;
  private final AuditUseCases auditUseCases;

  public AuditQueryServiceImpl(BusinessSupport support, AuditUseCases auditUseCases) {
    this.support = support;
    this.auditUseCases = auditUseCases;
  }

  @Override
  public UseCaseResult<PageResult<Views.AuditLog>> logs(
      RequestContext context, Queries.Audit query) {
    return auditUseCases.logs(context, query);
  }

  @Override
  public UseCaseResult<Views.AuditObject> singleObject(
      RequestContext context, AuditObjectType objectType, UUID objectId) {
    return auditUseCases.singleObject(context, objectType, objectId);
  }
}
