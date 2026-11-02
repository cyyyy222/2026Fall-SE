package com.campus.ticketing.service;

import com.campus.ticketing.common.*;
import com.campus.ticketing.dto.queries.Queries;
import com.campus.ticketing.dto.views.Views;
import com.campus.ticketing.enums.*;
import java.util.*;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;

public interface PublicEventService {
  UseCaseResult<PageResult<Views.SessionSearchRow>> listSessions(
      RequestContext context, Queries.PublicSessions query);

  UseCaseResult<Views.EventPublic> eventDetail(RequestContext context, UUID eventId);

  DownloadResult publicPoster(RequestContext context, UUID eventId);
}
