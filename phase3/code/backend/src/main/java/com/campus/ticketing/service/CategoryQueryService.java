package com.campus.ticketing.service;

import com.campus.ticketing.common.*;
import com.campus.ticketing.dto.queries.Queries;
import com.campus.ticketing.dto.views.Views;
import com.campus.ticketing.enums.*;
import java.util.*;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;

public interface CategoryQueryService {
  UseCaseResult<List<Views.Category>> listCategories(
      RequestContext context, Queries.Categories query);
}
