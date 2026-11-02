package com.campus.ticketing.service.impl;

import com.campus.ticketing.common.*;
import com.campus.ticketing.dto.requests.Requests;
import com.campus.ticketing.dto.views.Views;
import com.campus.ticketing.enums.*;
import com.campus.ticketing.service.ExportService;
import com.campus.ticketing.service.core.*;
import java.util.*;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.bind.annotation.*;

/**
 * Typed application facade; rules and transactions are implemented in the domain use-case service.
 */
@Service
public class ExportServiceImpl implements ExportService {
  private final BusinessSupport support;
  private final ReportingUseCases reportingUseCases;
  private final DurableWorkflows workflows;

  public ExportServiceImpl(
      BusinessSupport support, ReportingUseCases reportingUseCases, DurableWorkflows workflows) {
    this.support = support;
    this.reportingUseCases = reportingUseCases;
    this.workflows = workflows;
  }

  @Override
  public UseCaseResult<Views.ExportView> createExport(
      RequestContext context, UUID eventId, Requests.Export request) {
    return reportingUseCases.createExport(context, eventId, request);
  }

  @Override
  public UseCaseResult<Views.ExportView> exportStatus(RequestContext context, UUID exportId) {
    return reportingUseCases.exportStatus(context, exportId);
  }

  @Override
  public DownloadResult downloadCsv(RequestContext context, UUID exportId) {
    return reportingUseCases.downloadCsv(context, exportId);
  }

  @Override
  public void generatePending(UUID jobId) {
    workflows.process(jobId);
  }
}
