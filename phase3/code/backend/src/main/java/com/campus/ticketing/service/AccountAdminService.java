package com.campus.ticketing.service;

import com.campus.ticketing.common.*;
import com.campus.ticketing.dto.queries.Queries;
import com.campus.ticketing.dto.requests.Requests;
import com.campus.ticketing.dto.views.Views;
import com.campus.ticketing.enums.*;
import java.util.*;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;

public interface AccountAdminService {
  UseCaseResult<PageResult<Views.ManagedAccount>> listAccounts(
      RequestContext context, Queries.Accounts query);

  UseCaseResult<Views.ManagedAccount> createAccount(
      RequestContext context, Requests.CreateAccount request);

  UseCaseResult<Mutation<Views.ManagedAccount>> updateAccount(
      RequestContext context, UUID accountId, Requests.UpdateAccount request);

  UseCaseResult<Mutation<Views.ManagedAccount>> deleteAccount(
      RequestContext context, UUID accountId, Queries.Version query);

  UseCaseResult<Views.ResetPassword> resetPassword(
      RequestContext context, UUID accountId, Requests.ResetPassword request);

  UseCaseResult<PageResult<Views.RecoveryView>> listRecoveries(
      RequestContext context, Queries.Recoveries query);

  UseCaseResult<Views.RecoveryView> resolveRecovery(
      RequestContext context, UUID requestId, Requests.ResolveRecovery request);
}
