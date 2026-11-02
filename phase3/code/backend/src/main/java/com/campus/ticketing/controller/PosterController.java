package com.campus.ticketing.controller;

import com.campus.ticketing.common.*;
import com.campus.ticketing.enums.*;
import com.campus.ticketing.service.*;
import java.util.*;
import org.springframework.core.io.Resource;
import org.springframework.http.*;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api")
@Validated
public class PosterController {
  private final ApiResponses responses;
  private final PosterService posterService;

  public PosterController(ApiResponses responses, PosterService posterService) {
    this.responses = responses;
    this.posterService = posterService;
  }

  @ApiContract(
      id = "MGT-08",
      idempotency = false,
      query = {})
  @PreAuthorize("hasAnyRole('ORGANIZER','ADMIN')")
  @GetMapping(value = "/management/events/{eventId}/poster")
  public ResponseEntity<Resource> managementPoster(
      @CurrentRequest RequestContext context, @PathVariable UUID eventId) {
    return responses.download(posterService.managementPoster(context, eventId));
  }
}
