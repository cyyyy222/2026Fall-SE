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
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api")
@Validated
public class OrganizerEventController {
  private final ApiResponses responses;
  private final EventService eventService;

  public OrganizerEventController(ApiResponses responses, EventService eventService) {
    this.responses = responses;
    this.eventService = eventService;
  }

  @ApiContract(
      id = "ORG-01",
      idempotency = false,
      query = {"status", "page", "pageSize"})
  @PreAuthorize("hasAnyRole('ORGANIZER')")
  @GetMapping(value = "/organizer/events")
  public ResponseEntity<ApiResponse<PageResult<Views.EventManage>>> listMine(
      @CurrentRequest RequestContext context, @Valid @ModelAttribute Queries.Events query) {
    return responses.toResponse(eventService.listMine(context, query));
  }

  @ApiContract(
      id = "ORG-02",
      idempotency = true,
      query = {})
  @PreAuthorize("hasAnyRole('ORGANIZER')")
  @PostMapping(value = "/organizer/events")
  public ResponseEntity<ApiResponse<Views.EventManage>> createDraft(
      @CurrentRequest RequestContext context, @Valid @RequestBody Requests.Empty request) {
    return responses.toResponse(eventService.createDraft(context, request));
  }

  @ApiContract(
      id = "ORG-03",
      idempotency = false,
      query = {})
  @PreAuthorize("hasAnyRole('ORGANIZER')")
  @GetMapping(value = "/organizer/events/{eventId}")
  public ResponseEntity<ApiResponse<Views.EventManage>> managementDetail(
      @CurrentRequest RequestContext context, @PathVariable UUID eventId) {
    return responses.toResponse(eventService.managementDetail(context, eventId));
  }

  @ApiContract(
      id = "ORG-04",
      idempotency = true,
      query = {})
  @PreAuthorize("hasAnyRole('ORGANIZER')")
  @PutMapping(value = "/organizer/events/{eventId}/draft")
  public ResponseEntity<ApiResponse<Mutation<Views.EventManage>>> saveDraft(
      @CurrentRequest RequestContext context,
      @PathVariable UUID eventId,
      @Valid @RequestBody Requests.Draft request) {
    return responses.toResponse(eventService.saveDraft(context, eventId, request));
  }

  @ApiContract(
      id = "ORG-05",
      idempotency = true,
      query = {"expectedVersion"})
  @PreAuthorize("hasAnyRole('ORGANIZER')")
  @PutMapping(
      value = "/organizer/events/{eventId}/poster",
      consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
  public ResponseEntity<ApiResponse<Mutation<Views.EventManage>>> replacePoster(
      @CurrentRequest RequestContext context,
      @PathVariable UUID eventId,
      @RequestPart("file") MultipartFile file,
      @jakarta.validation.constraints.NotNull
          @jakarta.validation.constraints.Min(0)
          @RequestParam("expectedVersion")
          Long expectedVersion)
      throws java.io.IOException {
    if (file.getSize() > 5_000_000) throw new ApiException(413, "FILE_TOO_LARGE", "海报超过大小限制");
    Requests.PosterUpload request =
        new Requests.PosterUpload(expectedVersion, file.getBytes(), file.getContentType());
    return responses.toResponse(eventService.replacePoster(context, eventId, request));
  }

  @ApiContract(
      id = "ORG-06",
      idempotency = true,
      query = {"expectedVersion"})
  @PreAuthorize("hasAnyRole('ORGANIZER')")
  @DeleteMapping(value = "/organizer/events/{eventId}/poster")
  public ResponseEntity<ApiResponse<Mutation<Views.EventManage>>> removePoster(
      @CurrentRequest RequestContext context,
      @PathVariable UUID eventId,
      @Valid @ModelAttribute Queries.Version query) {
    return responses.toResponse(eventService.removePoster(context, eventId, query));
  }

  @ApiContract(
      id = "ORG-07",
      idempotency = true,
      query = {})
  @PreAuthorize("hasAnyRole('ORGANIZER')")
  @PostMapping(value = "/organizer/events/{eventId}/submission")
  public ResponseEntity<ApiResponse<Mutation<Views.EventManage>>> submitReview(
      @CurrentRequest RequestContext context,
      @PathVariable UUID eventId,
      @Valid @RequestBody Requests.Version request) {
    return responses.toResponse(eventService.submitReview(context, eventId, request));
  }

  @ApiContract(
      id = "ORG-08",
      idempotency = true,
      query = {})
  @PreAuthorize("hasAnyRole('ORGANIZER')")
  @PostMapping(value = "/organizer/events/{eventId}/withdrawal")
  public ResponseEntity<ApiResponse<Mutation<Views.EventManage>>> withdrawReview(
      @CurrentRequest RequestContext context,
      @PathVariable UUID eventId,
      @Valid @RequestBody Requests.Version request) {
    return responses.toResponse(eventService.withdrawReview(context, eventId, request));
  }

  @ApiContract(
      id = "ORG-09",
      idempotency = true,
      query = {})
  @PreAuthorize("hasAnyRole('ORGANIZER')")
  @PatchMapping(value = "/organizer/events/{eventId}/published-settings")
  public ResponseEntity<ApiResponse<Mutation<Views.EventManage>>> updatePublishedSettings(
      @CurrentRequest RequestContext context,
      @PathVariable UUID eventId,
      @Valid @RequestBody Requests.PublishedEdit request) {
    return responses.toResponse(eventService.updatePublishedSettings(context, eventId, request));
  }

  @ApiContract(
      id = "ORG-10",
      idempotency = true,
      query = {})
  @PreAuthorize("hasAnyRole('ORGANIZER')")
  @PostMapping(value = "/organizer/events/{eventId}/cancellation")
  public ResponseEntity<ApiResponse<Views.TerminationResult>> cancelEvent(
      @CurrentRequest RequestContext context,
      @PathVariable UUID eventId,
      @Valid @RequestBody Requests.VersionReason request) {
    return responses.toResponse(eventService.cancelEvent(context, eventId, request));
  }

  @ApiContract(
      id = "ORG-11",
      idempotency = false,
      query = {})
  @PreAuthorize("hasAnyRole('ORGANIZER')")
  @GetMapping(value = "/organizer/events/{eventId}/settlement")
  public ResponseEntity<ApiResponse<Views.SettlementProgress>> settlementProgress(
      @CurrentRequest RequestContext context, @PathVariable UUID eventId) {
    return responses.toResponse(eventService.settlementProgress(context, eventId));
  }
}
