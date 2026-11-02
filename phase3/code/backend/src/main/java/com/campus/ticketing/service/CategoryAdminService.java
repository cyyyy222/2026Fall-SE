package com.campus.ticketing.service;

import com.campus.ticketing.common.*;
import com.campus.ticketing.dto.queries.Queries;
import com.campus.ticketing.dto.requests.Requests;
import com.campus.ticketing.dto.views.Views;
import com.campus.ticketing.enums.*;
import java.util.*;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;

public interface CategoryAdminService {
  UseCaseResult<PageResult<Views.Category>> listCategories(
      RequestContext context, Queries.CategoryAdmin query);

  UseCaseResult<Views.Category> createCategory(
      RequestContext context, Requests.CreateCategory request);

  UseCaseResult<Mutation<Views.Category>> updateCategory(
      RequestContext context, UUID categoryId, Requests.UpdateCategory request);
}
