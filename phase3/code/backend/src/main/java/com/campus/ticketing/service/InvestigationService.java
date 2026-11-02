package com.campus.ticketing.service;

import com.campus.ticketing.common.*;
import com.campus.ticketing.dto.queries.Queries;
import com.campus.ticketing.dto.requests.Requests;
import com.campus.ticketing.dto.views.Views;
import com.campus.ticketing.enums.*;
import java.util.*;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;

public interface InvestigationService {
  UseCaseResult<PageResult<Views.ReportAdminRow>> listReports(
      RequestContext context, Queries.AdminReports query);

  UseCaseResult<Views.InvestigationResult> investigate(
      RequestContext context, Requests.Investigation request);

  UseCaseResult<Views.InvestigationDetail> investigationDetail(
      RequestContext context, UUID investigationId);

  UseCaseResult<PageResult<Views.InvestigationResult>> listInvestigations(
      RequestContext context, Queries.Investigations query);
}
