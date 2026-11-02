package com.campus.ticketing.service;

import com.campus.ticketing.common.*;
import com.campus.ticketing.dto.requests.Requests;
import com.campus.ticketing.dto.views.Views;
import com.campus.ticketing.enums.*;
import java.util.*;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;

public interface ExportService {
  UseCaseResult<Views.ExportView> createExport(
      RequestContext context, UUID eventId, Requests.Export request);

  UseCaseResult<Views.ExportView> exportStatus(RequestContext context, UUID exportId);

  DownloadResult downloadCsv(RequestContext context, UUID exportId);

  void generatePending(UUID jobId);
}
