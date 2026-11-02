package com.campus.ticketing.service;

import com.campus.ticketing.common.*;
import com.campus.ticketing.dto.queries.Queries;
import com.campus.ticketing.dto.requests.Requests;
import com.campus.ticketing.dto.views.Views;
import com.campus.ticketing.enums.*;
import java.util.*;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;

public interface MessageService {
  UseCaseResult<PageResult<Views.MessageView>> listMine(
      RequestContext context, Queries.Messages query);

  UseCaseResult<Views.MessageView> messageDetail(RequestContext context, UUID messageId);

  UseCaseResult<Mutation<Views.MessageView>> markRead(
      RequestContext context, UUID messageId, Requests.Empty request);
}
