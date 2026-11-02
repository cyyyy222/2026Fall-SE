package com.campus.ticketing.service;

import com.campus.ticketing.common.*;
import com.campus.ticketing.dto.requests.Requests;
import com.campus.ticketing.dto.views.Views;
import com.campus.ticketing.enums.*;
import java.util.*;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;

public interface CheckInService {
  UseCaseResult<Views.CheckerContext> context(RequestContext context);

  UseCaseResult<Views.TicketPreview> previewTicket(RequestContext context, Requests.Ticket request);

  UseCaseResult<Views.CheckInResult> confirmCheckIn(
      RequestContext context, Requests.Ticket request);

  UseCaseResult<Views.Reconciliation> reconcileAbsence(
      RequestContext context, UUID sessionId, Requests.Empty request);
}
