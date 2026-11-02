package com.campus.ticketing.controller;

import com.campus.ticketing.common.*;
import com.campus.ticketing.dto.queries.Queries;
import com.campus.ticketing.dto.requests.Requests;
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
public class AdminCategoryController {
  private final ApiResponses responses;
  private final CategoryAdminService categoryAdminService;

  public AdminCategoryController(
      ApiResponses responses, CategoryAdminService categoryAdminService) {
    this.responses = responses;
    this.categoryAdminService = categoryAdminService;
  }

  @ApiContract(
      id = "ADM-12",
      idempotency = false,
      query = {"enabled", "page", "pageSize"})
  @PreAuthorize("hasAnyRole('ADMIN')")
  @GetMapping(value = "/admin/categories")
  public ResponseEntity<ApiResponse<PageResult<Views.Category>>> listCategories(
      @CurrentRequest RequestContext context, @Valid @ModelAttribute Queries.CategoryAdmin query) {
    return responses.toResponse(categoryAdminService.listCategories(context, query));
  }

  @ApiContract(
      id = "ADM-13",
      idempotency = true,
      query = {})
  @PreAuthorize("hasAnyRole('ADMIN')")
  @PostMapping(value = "/admin/categories")
  public ResponseEntity<ApiResponse<Views.Category>> createCategory(
      @CurrentRequest RequestContext context, @Valid @RequestBody Requests.CreateCategory request) {
    return responses.toResponse(categoryAdminService.createCategory(context, request));
  }

  @ApiContract(
      id = "ADM-14",
      idempotency = true,
      query = {})
  @PreAuthorize("hasAnyRole('ADMIN')")
  @PatchMapping(value = "/admin/categories/{categoryId}")
  public ResponseEntity<ApiResponse<Mutation<Views.Category>>> updateCategory(
      @CurrentRequest RequestContext context,
      @PathVariable UUID categoryId,
      @Valid @RequestBody Requests.UpdateCategory request) {
    return responses.toResponse(categoryAdminService.updateCategory(context, categoryId, request));
  }
}
