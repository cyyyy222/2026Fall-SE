package com.campus.ticketing.service;

import com.campus.ticketing.common.*;
import com.campus.ticketing.dto.views.Views;
import com.campus.ticketing.enums.*;
import java.util.*;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;

public interface TicketService {
  UseCaseResult<Views.TicketView> ticketDetail(RequestContext context, UUID ticketId);

  DownloadResult downloadText(RequestContext context, UUID ticketId);
}
