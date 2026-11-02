package com.campus.ticketing.service;

import com.campus.ticketing.common.*;
import com.campus.ticketing.dto.queries.Queries;
import com.campus.ticketing.dto.requests.Requests;
import com.campus.ticketing.dto.views.Views;
import com.campus.ticketing.enums.*;
import java.util.*;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;

public interface EventReviewService {
  UseCaseResult<Views.ReviewQueue> reviewQueue(RequestContext context, Queries.Reviews query);

  UseCaseResult<Views.EventManage> eventDetail(RequestContext context, UUID eventId);

  UseCaseResult<Views.ReviewResult> approve(
      RequestContext context, UUID eventId, Requests.Version request);

  UseCaseResult<Views.ReviewResult> reject(
      RequestContext context, UUID eventId, Requests.VersionReason request);

  UseCaseResult<PageResult<Views.EventManage>> listEvents(
      RequestContext context, Queries.AdminEvents query);
}
