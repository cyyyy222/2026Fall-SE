package com.campus.ticketing.service;

import com.campus.ticketing.common.*;
import com.campus.ticketing.dto.queries.Queries;
import com.campus.ticketing.dto.views.Views;
import com.campus.ticketing.enums.*;
import java.util.*;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;

public interface AuditQueryService {
  UseCaseResult<PageResult<Views.AuditLog>> logs(RequestContext context, Queries.Audit query);

  UseCaseResult<Views.AuditObject> singleObject(
      RequestContext context, AuditObjectType objectType, UUID objectId);
}
