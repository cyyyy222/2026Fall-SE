package com.campus.ticketing.service.impl;

import com.campus.ticketing.common.*;
import com.campus.ticketing.dto.queries.Queries;
import com.campus.ticketing.dto.requests.Requests;
import com.campus.ticketing.dto.views.Views;
import com.campus.ticketing.enums.*;
import com.campus.ticketing.service.AccountAdminService;
import com.campus.ticketing.service.core.*;
import java.util.*;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.bind.annotation.*;

/**
 * Typed application facade; rules and transactions are implemented in the domain use-case service.
 */
@Service
public class AccountAdminServiceImpl implements AccountAdminService {
  private final BusinessSupport support;
  private final AdminAccountUseCases adminAccountUseCases;

  public AccountAdminServiceImpl(
      BusinessSupport support, AdminAccountUseCases adminAccountUseCases) {
    this.support = support;
    this.adminAccountUseCases = adminAccountUseCases;
  }

  @Override
  public UseCaseResult<PageResult<Views.ManagedAccount>> listAccounts(
      RequestContext context, Queries.Accounts query) {
    return adminAccountUseCases.listAccounts(context, query);
  }

  @Override
  public UseCaseResult<Views.ManagedAccount> createAccount(
      RequestContext context, Requests.CreateAccount request) {
    return adminAccountUseCases.createAccount(context, request);
  }

  @Override
  public UseCaseResult<Mutation<Views.ManagedAccount>> updateAccount(
      RequestContext context, UUID accountId, Requests.UpdateAccount request) {
    return adminAccountUseCases.updateAccount(context, accountId, request);
  }

  @Override
  public UseCaseResult<Mutation<Views.ManagedAccount>> deleteAccount(
      RequestContext context, UUID accountId, Queries.Version query) {
    return adminAccountUseCases.deleteAccount(context, accountId, query);
  }

  @Override
  public UseCaseResult<Views.ResetPassword> resetPassword(
      RequestContext context, UUID accountId, Requests.ResetPassword request) {
    return adminAccountUseCases.resetPassword(context, accountId, request);
  }

  @Override
  public UseCaseResult<PageResult<Views.RecoveryView>> listRecoveries(
      RequestContext context, Queries.Recoveries query) {
    return adminAccountUseCases.listRecoveries(context, query);
  }

  @Override
  public UseCaseResult<Views.RecoveryView> resolveRecovery(
      RequestContext context, UUID requestId, Requests.ResolveRecovery request) {
    return adminAccountUseCases.resolveRecovery(context, requestId, request);
  }
}
