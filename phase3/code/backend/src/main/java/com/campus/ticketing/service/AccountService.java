package com.campus.ticketing.service;

import com.campus.ticketing.common.*;
import com.campus.ticketing.dto.requests.Requests;
import com.campus.ticketing.dto.views.Views;
import com.campus.ticketing.enums.*;
import java.util.*;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;

public interface AccountService {
  UseCaseResult<Views.ChangedPhone> changePhone(
      RequestContext context, Requests.ChangePhone request);

  UseCaseResult<Views.Identity> currentUser(RequestContext context);

  UseCaseResult<Mutation<Views.UserView>> updateProfile(
      RequestContext context, Requests.Profile request);

  UseCaseResult<Views.ChangedPassword> changePassword(
      RequestContext context, Requests.Password request);

  UseCaseResult<Views.ClosureEligibility> closureEligibility(RequestContext context);

  UseCaseResult<Views.ClosedAccount> closeAccount(RequestContext context, Requests.Closure request);
}
