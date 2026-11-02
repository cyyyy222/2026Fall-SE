package com.campus.ticketing.service;

import com.campus.ticketing.common.*;
import com.campus.ticketing.dto.requests.Requests;
import com.campus.ticketing.dto.views.Views;
import com.campus.ticketing.enums.*;
import java.util.*;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;

public interface RegistrationService {
  UseCaseResult<Views.RegistrationResult> register(
      RequestContext context, Requests.Registration request);

  UseCaseResult<Views.RegistrationResult> findByRequest(RequestContext context, UUID requestId);
}
