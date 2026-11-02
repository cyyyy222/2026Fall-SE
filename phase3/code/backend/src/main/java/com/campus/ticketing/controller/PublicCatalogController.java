package com.campus.ticketing.controller;

import com.campus.ticketing.common.*;
import com.campus.ticketing.dto.queries.Queries;
import com.campus.ticketing.dto.views.Views;
import com.campus.ticketing.enums.*;
import com.campus.ticketing.service.*;
import jakarta.validation.Valid;
import java.util.*;
import org.springframework.http.*;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api")
@Validated
public class PublicCatalogController {
  private final ApiResponses responses;
  private final CategoryQueryService categoryQueryService;

  public PublicCatalogController(
      ApiResponses responses, CategoryQueryService categoryQueryService) {
    this.responses = responses;
    this.categoryQueryService = categoryQueryService;
  }

  @ApiContract(
      id = "PUB-01",
      idempotency = false,
      query = {"scope"})
  @PreAuthorize("permitAll()")
  @GetMapping(value = "/public/categories")
  public ResponseEntity<ApiResponse<List<Views.Category>>> listCategories(
      @CurrentRequest RequestContext context, @Valid @ModelAttribute Queries.Categories query) {
    return responses.toResponse(categoryQueryService.listCategories(context, query));
  }
}
