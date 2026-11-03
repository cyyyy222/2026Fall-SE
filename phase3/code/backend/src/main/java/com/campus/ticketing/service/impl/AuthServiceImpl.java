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
  private final PhoneCodeUseCases phoneCodes;

  public AuthServiceImpl(
      BusinessSupport support,
      IdentityUseCases identityUseCases,
      RateLimitService rateLimitService,
      PhoneCodeUseCases phoneCodes) {
    this.support = support;
    this.identityUseCases = identityUseCases;
    this.rateLimitService = rateLimitService;
    this.phoneCodes = phoneCodes;
  }

  @Override
  public UseCaseResult<Views.PhoneCodeReceipt> issuePhoneCode(
      RequestContext c, Requests.PhoneCode r) {
    return phoneCodes.issue(c, r);
  }

  @Override
  public UseCaseResult<Views.Identity> phoneLogin(RequestContext c, Requests.PhoneLogin r) {
    support.db();
    BusinessSupport.valid(
        r.purpose() == Requests.PhoneCodePurpose.LOGIN
            || r.purpose() == Requests.PhoneCodePurpose.REGISTER,
        "该验证码用途不能用于登录");
    rateLimitService.check("login", c, r.phone() + ":" + r.role());
    phoneCodes.verify(
        r.phone(), r.role(), r.purpose(), r.verificationId(), r.verificationCode(), true);
    return identityUseCases.loginByPhone(c, r);
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
    phoneCodes.verify(
        request.phone(),
        request.role(),
        Requests.PhoneCodePurpose.REGISTER,
        request.verificationId(),
        request.verificationCode(),
        false);
    if (identityUseCases.hasPhoneAccount(request.phone(), request.role()))
      throw new ApiException(409, "PHONE_ACCOUNT_EXISTS", "该手机号已有当前角色的账号，这是你的账号吗？");
    phoneCodes.verify(
        request.phone(),
        request.role(),
        Requests.PhoneCodePurpose.REGISTER,
        request.verificationId(),
        request.verificationCode(),
        true);
    return identityUseCases.register(context, request);
  }

  @Override
  public UseCaseResult<Views.RecoveryReceipt> requestRecovery(
      RequestContext context, Requests.Recovery request) {
    support.db();
    rateLimitService.check("recovery", context, request.account());
    phoneCodes.verify(
        request.currentPhone(),
        request.role(),
        Requests.PhoneCodePurpose.RECOVERY_CONTACT,
        request.verificationId(),
        request.verificationCode(),
        true);
    return identityUseCases.requestRecovery(context, request);
  }

  @Override
  public UseCaseResult<Views.ChangedPassword> resetPasswordByPhone(
      RequestContext c, Requests.PhonePasswordReset r) {
    support.db();
    rateLimitService.check("login", c, r.phone() + ":" + r.role());
    phoneCodes.verify(
        r.phone(),
        r.role(),
        Requests.PhoneCodePurpose.RESET_PASSWORD,
        r.verificationId(),
        r.verificationCode(),
        true);
    return identityUseCases.resetPasswordByPhone(c, r);
  }

  @Override
  public UseCaseResult<Views.LoggedOut> logout(RequestContext context, Requests.Empty request) {
    return identityUseCases.logout(context, request);
  }
}
