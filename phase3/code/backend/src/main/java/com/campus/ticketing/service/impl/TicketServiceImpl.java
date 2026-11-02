package com.campus.ticketing.service.impl;

import com.campus.ticketing.common.*;
import com.campus.ticketing.dto.views.Views;
import com.campus.ticketing.enums.*;
import com.campus.ticketing.service.TicketService;
import com.campus.ticketing.service.core.*;
import java.util.*;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.bind.annotation.*;

/**
 * Typed application facade; rules and transactions are implemented in the domain use-case service.
 */
@Service
public class TicketServiceImpl implements TicketService {
  private final BusinessSupport support;
  private final OrderUseCases orderUseCases;

  public TicketServiceImpl(BusinessSupport support, OrderUseCases orderUseCases) {
    this.support = support;
    this.orderUseCases = orderUseCases;
  }

  @Override
  public UseCaseResult<Views.TicketView> ticketDetail(RequestContext context, UUID ticketId) {
    return orderUseCases.ticketDetail(context, ticketId);
  }

  @Override
  public DownloadResult downloadText(RequestContext context, UUID ticketId) {
    return orderUseCases.downloadText(context, ticketId);
  }
}
