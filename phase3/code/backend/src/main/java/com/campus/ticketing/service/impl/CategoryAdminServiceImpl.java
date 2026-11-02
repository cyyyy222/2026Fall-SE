package com.campus.ticketing.service.impl;

import com.campus.ticketing.common.*;
import com.campus.ticketing.dto.queries.Queries;
import com.campus.ticketing.dto.requests.Requests;
import com.campus.ticketing.dto.views.Views;
import com.campus.ticketing.enums.*;
import com.campus.ticketing.service.CategoryAdminService;
import com.campus.ticketing.service.core.*;
import java.util.*;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.bind.annotation.*;

/**
 * Typed application facade; rules and transactions are implemented in the domain use-case service.
 */
@Service
public class CategoryAdminServiceImpl implements CategoryAdminService {
  private final BusinessSupport support;
  private final EventUseCases eventUseCases;

  public CategoryAdminServiceImpl(BusinessSupport support, EventUseCases eventUseCases) {
    this.support = support;
    this.eventUseCases = eventUseCases;
  }

  @Override
  public UseCaseResult<PageResult<Views.Category>> listCategories(
      RequestContext context, Queries.CategoryAdmin query) {
    return eventUseCases.listCategoriesAdmin(context, query);
  }

  @Override
  public UseCaseResult<Views.Category> createCategory(
      RequestContext context, Requests.CreateCategory request) {
    return eventUseCases.createCategory(context, request);
  }

  @Override
  public UseCaseResult<Mutation<Views.Category>> updateCategory(
      RequestContext context, UUID categoryId, Requests.UpdateCategory request) {
    return eventUseCases.updateCategory(context, categoryId, request);
  }
}
