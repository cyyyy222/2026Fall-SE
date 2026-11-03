package com.campus.ticketing.service;

import com.campus.ticketing.common.*;
import com.campus.ticketing.dto.requests.Requests;
import com.campus.ticketing.dto.views.Views;
import com.campus.ticketing.enums.*;
import java.util.*;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;

public interface AuthService {
  UseCaseResult<Views.ChangedPassword> resetPasswordByPhone(
      RequestContext context, Requests.PhonePasswordReset request);

  UseCaseResult<Views.PhoneCodeReceipt> issuePhoneCode(
      RequestContext context, Requests.PhoneCode request);

  UseCaseResult<Views.Identity> phoneLogin(RequestContext context, Requests.PhoneLogin request);

  UseCaseResult<Views.Identity> login(RequestContext context, Requests.Login request);

  UseCaseResult<Views.Registered> register(RequestContext context, Requests.Register request);

  UseCaseResult<Views.RecoveryReceipt> requestRecovery(
      RequestContext context, Requests.Recovery request);

  UseCaseResult<Views.LoggedOut> logout(RequestContext context, Requests.Empty request);
}
