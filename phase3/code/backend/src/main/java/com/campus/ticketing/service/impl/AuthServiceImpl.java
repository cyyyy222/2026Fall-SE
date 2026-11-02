package com.campus.ticketing.service.impl;

import com.campus.ticketing.common.*;
import com.campus.ticketing.dto.requests.Requests;
import com.campus.ticketing.dto.views.Views;
import com.campus.ticketing.enums.*;
import com.campus.ticketing.service.AuthService;
import com.campus.ticketing.service.core.*;
import java.util.*;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.bind.annotation.*;

/**
 * Typed application facade; rules and transactions are implemented in the domain use-case service.
 */
@Service
public class AuthServiceImpl implements AuthService {
  private final BusinessSupport support;
  private final IdentityUseCases identityUseCases;
  private final RateLimitService rateLimitService;

  public AuthServiceImpl(
      BusinessSupport support,
      IdentityUseCases identityUseCases,
      RateLimitService rateLimitService) {
    this.support = support;
    this.identityUseCases = identityUseCases;
    this.rateLimitService = rateLimitService;
  }

  @Override
  public UseCaseResult<Views.Identity> login(RequestContext context, Requests.Login request) {
    support.db();
    rateLimitService.check("login", context, request.username());
    return identityUseCases.login(context, request);
  }

  @Override
  public UseCaseResult<Views.Registered> register(
      RequestContext context, Requests.Register request) {
    support.db();
    rateLimitService.check("register", context, request.username());
    return identityUseCases.register(context, request);
  }

  @Override
  public UseCaseResult<Views.RecoveryReceipt> requestRecovery(
      RequestContext context, Requests.Recovery request) {
    support.db();
    rateLimitService.check("recovery", context, request.account());
    return identityUseCases.requestRecovery(context, request);
  }

  @Override
  public UseCaseResult<Views.LoggedOut> logout(RequestContext context, Requests.Empty request) {
    return identityUseCases.logout(context, request);
  }
}
