package com.campus.ticketing.service.impl;

import com.campus.ticketing.entity.JobEntity;
import com.campus.ticketing.service.NaturalSettlementService;
import com.campus.ticketing.service.core.*;
import java.util.UUID;
import org.springframework.stereotype.Service;

@Service
public class NaturalSettlementServiceImpl implements NaturalSettlementService {
  private final BusinessSupport support;
  private final DurableWorkflows workflows;

  public NaturalSettlementServiceImpl(BusinessSupport support, DurableWorkflows workflows) {
    this.support = support;
    this.workflows = workflows;
  }

  @Override
  public void settleDueSessions(UUID jobId) {
    support.read(
        () -> {
          BusinessSupport.valid(
              support.need(JobEntity.class, jobId, false).type().equals("JOB-03"), "内部任务类型不匹配");
          return null;
        });
    workflows.process(jobId);
  }
}
