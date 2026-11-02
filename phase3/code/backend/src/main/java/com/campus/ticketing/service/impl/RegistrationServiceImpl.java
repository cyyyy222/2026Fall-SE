package com.campus.ticketing.service.impl;

import com.campus.ticketing.common.*;
import com.campus.ticketing.dto.requests.Requests;
import com.campus.ticketing.dto.views.Views;
import com.campus.ticketing.enums.*;
import com.campus.ticketing.service.RegistrationService;
import com.campus.ticketing.service.core.*;
import java.util.*;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.bind.annotation.*;

/**
 * Typed application facade; rules and transactions are implemented in the domain use-case service.
 */
@Service
public class RegistrationServiceImpl implements RegistrationService {
  private final BusinessSupport support;
  private final OrderUseCases orderUseCases;

  public RegistrationServiceImpl(BusinessSupport support, OrderUseCases orderUseCases) {
    this.support = support;
    this.orderUseCases = orderUseCases;
  }

  @Override
  public UseCaseResult<Views.RegistrationResult> register(
      RequestContext context, Requests.Registration request) {
    return orderUseCases.register(context, request);
  }

  @Override
  public UseCaseResult<Views.RegistrationResult> findByRequest(
      RequestContext context, UUID requestId) {
    return orderUseCases.findByRequest(context, requestId);
  }
}
