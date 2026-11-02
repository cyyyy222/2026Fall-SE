package com.campus.ticketing.scheduler;

import com.campus.ticketing.service.core.DurableWorkflows;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.*;

@Configuration
@EnableScheduling
@ConditionalOnProperty(name = "app.jobs.enabled", havingValue = "true")
public class DurableScheduler {
  private final DurableWorkflows workflows;

  public DurableScheduler(DurableWorkflows workflows) {
    this.workflows = workflows;
  }

  @Scheduled(fixedDelayString = "${app.jobs.poll-ms:5000}")
  public void run() {
    workflows.runDue();
  }
}
