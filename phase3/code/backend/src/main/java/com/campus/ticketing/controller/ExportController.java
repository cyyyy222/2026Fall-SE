package com.campus.ticketing.controller;

import com.campus.ticketing.common.*;
import com.campus.ticketing.dto.requests.Requests;
import com.campus.ticketing.dto.views.Views;
import com.campus.ticketing.enums.*;
import com.campus.ticketing.service.*;
import jakarta.validation.Valid;
import java.util.*;
import org.springframework.core.io.Resource;
import org.springframework.http.*;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api")
@Validated
public class ExportController {
  private final ApiResponses responses;
  private final ExportService exportService;

  public ExportController(ApiResponses responses, ExportService exportService) {
    this.responses = responses;
    this.exportService = exportService;
  }

  @ApiContract(
      id = "MGT-02",
      idempotency = true,
      query = {})
  @PreAuthorize("hasAnyRole('ORGANIZER','ADMIN')")
  @PostMapping(value = "/management/events/{eventId}/exports")
  public ResponseEntity<ApiResponse<Views.ExportView>> createExport(
      @CurrentRequest RequestContext context,
      @PathVariable UUID eventId,
      @Valid @RequestBody Requests.Export request) {
    return responses.toResponse(exportService.createExport(context, eventId, request));
  }

  @ApiContract(
      id = "MGT-03",
      idempotency = false,
      query = {})
  @PreAuthorize("hasAnyRole('ORGANIZER','ADMIN')")
  @GetMapping(value = "/management/exports/{exportId}")
  public ResponseEntity<ApiResponse<Views.ExportView>> exportStatus(
      @CurrentRequest RequestContext context, @PathVariable UUID exportId) {
    return responses.toResponse(exportService.exportStatus(context, exportId));
  }

  @ApiContract(
      id = "MGT-04",
      idempotency = false,
      query = {})
  @PreAuthorize("hasAnyRole('ORGANIZER','ADMIN')")
  @GetMapping(value = "/management/exports/{exportId}/download")
  public ResponseEntity<Resource> downloadCsv(
      @CurrentRequest RequestContext context, @PathVariable UUID exportId) {
    return responses.download(exportService.downloadCsv(context, exportId));
  }
}
