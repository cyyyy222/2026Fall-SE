package com.campus.ticketing.service.impl;

import com.campus.ticketing.common.*;
import com.campus.ticketing.dto.queries.Queries;
import com.campus.ticketing.dto.views.Views;
import com.campus.ticketing.enums.*;
import com.campus.ticketing.service.CategoryQueryService;
import com.campus.ticketing.service.core.*;
import java.util.*;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.bind.annotation.*;

/**
 * Typed application facade; rules and transactions are implemented in the domain use-case service.
 */
@Service
public class CategoryQueryServiceImpl implements CategoryQueryService {
  private final BusinessSupport support;
  private final EventUseCases eventUseCases;

  public CategoryQueryServiceImpl(BusinessSupport support, EventUseCases eventUseCases) {
    this.support = support;
    this.eventUseCases = eventUseCases;
  }

  @Override
  public UseCaseResult<List<Views.Category>> listCategories(
      RequestContext context, Queries.Categories query) {
    return eventUseCases.listCategories(context, query);
  }
}
