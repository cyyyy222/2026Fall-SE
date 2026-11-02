package com.campus.ticketing.service;

import com.campus.ticketing.dto.views.Views;

public interface SystemStatusService {
  Views.Health health();
}
