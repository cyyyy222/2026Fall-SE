package com.campus.ticketing.service.impl;

import com.campus.ticketing.common.*;
import com.campus.ticketing.dto.queries.Queries;
import com.campus.ticketing.dto.views.Views;
import com.campus.ticketing.enums.*;
import com.campus.ticketing.service.AttendeeService;
import com.campus.ticketing.service.core.*;
import java.util.*;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.bind.annotation.*;

/**
 * Typed application facade; rules and transactions are implemented in the domain use-case service.
 */
@Service
public class AttendeeServiceImpl implements AttendeeService {
  private final BusinessSupport support;
  private final ReportingUseCases reportingUseCases;

  public AttendeeServiceImpl(BusinessSupport support, ReportingUseCases reportingUseCases) {
    this.support = support;
    this.reportingUseCases = reportingUseCases;
  }

  @Override
  public UseCaseResult<PageResult<Views.AttendeeRow>> listAttendees(
      RequestContext context, UUID eventId, Queries.Attendees query) {
    return reportingUseCases.listAttendees(context, eventId, query);
  }
}
