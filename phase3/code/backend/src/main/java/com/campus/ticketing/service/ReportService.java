package com.campus.ticketing.service;

import com.campus.ticketing.common.*;
import com.campus.ticketing.dto.queries.Queries;
import com.campus.ticketing.dto.requests.Requests;
import com.campus.ticketing.dto.views.Views;
import com.campus.ticketing.enums.*;
import java.util.*;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;

public interface ReportService {
  UseCaseResult<Views.ReportView> createReport(RequestContext context, Requests.Report request);

  UseCaseResult<PageResult<Views.ReportView>> listMine(
      RequestContext context, Queries.Reports query);

  UseCaseResult<Views.ReportView> reportDetail(RequestContext context, UUID reportId);
}
