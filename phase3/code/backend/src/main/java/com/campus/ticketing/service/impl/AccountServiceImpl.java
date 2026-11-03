package com.campus.ticketing.service.impl;

import com.campus.ticketing.common.*;
import com.campus.ticketing.dto.requests.Requests;
import com.campus.ticketing.dto.views.Views;
import com.campus.ticketing.enums.*;
import com.campus.ticketing.service.AccountService;
import com.campus.ticketing.service.core.*;
import java.util.*;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.bind.annotation.*;

/**
 * Typed application facade; rules and transactions are implemented in the domain use-case service.
 */
@Service
public class AccountServiceImpl implements AccountService {
  private final BusinessSupport support;
  private final IdentityUseCases identityUseCases;
  private final PhoneCodeUseCases phoneCodes;

  public AccountServiceImpl(
      BusinessSupport support, IdentityUseCases identityUseCases, PhoneCodeUseCases phoneCodes) {
    this.support = support;
    this.identityUseCases = identityUseCases;
    this.phoneCodes = phoneCodes;
  }

  @Override
  public UseCaseResult<Views.ChangedPhone> changePhone(RequestContext c, Requests.ChangePhone r) {
    return identityUseCases.changePhone(
        c,
        r,
        () -> {
          var u = support.actor(c, Role.PARTICIPANT, Role.ORGANIZER);
          phoneCodes.verify(
              r.newPhone(),
              RegisterRole.valueOf(u.role()),
              Requests.PhoneCodePurpose.CHANGE_PHONE,
              r.verificationId(),
              r.verificationCode(),
              true);
        });
  }

  @Override
  public UseCaseResult<Views.Identity> currentUser(RequestContext context) {
    return identityUseCases.currentUser(context);
  }

  @Override
  public UseCaseResult<Mutation<Views.UserView>> updateProfile(
      RequestContext context, Requests.Profile request) {
    return identityUseCases.updateProfile(context, request);
  }

  @Override
  public UseCaseResult<Views.ChangedPassword> changePassword(
      RequestContext context, Requests.Password request) {
    return identityUseCases.changePassword(context, request);
  }

  @Override
  public UseCaseResult<Views.ClosureEligibility> closureEligibility(RequestContext context) {
    return identityUseCases.closureEligibility(context);
  }

  @Override
  public UseCaseResult<Views.ClosedAccount> closeAccount(
      RequestContext context, Requests.Closure request) {
    return identityUseCases.closeAccount(context, request);
  }
}
