package com.campus.ticketing.controller;

import com.campus.ticketing.common.*;
import com.campus.ticketing.dto.views.Views;
import com.campus.ticketing.service.SystemStatusService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api")
public class SystemController {
  private final SystemStatusService service;
  private final ApiResponses responses;

  public SystemController(SystemStatusService service, ApiResponses responses) {
    this.service = service;
    this.responses = responses;
  }

  @ApiContract(id = "SYS-01")
  @GetMapping("/system/health")
  public ResponseEntity<ApiResponse<Views.Health>> health() {
    var h = service.health();
    boolean ready = "UP".equals(h.status());
    return ResponseEntity.status(ready ? 200 : 503)
        .body(responses.result(ready ? "OK" : "SERVICE_UNAVAILABLE", ready ? "服务就绪" : "依赖尚未就绪", h));
  }
}
