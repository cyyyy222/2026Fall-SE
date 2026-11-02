package com.campus.ticketing.service.impl;

import com.campus.ticketing.entity.JobEntity;
import com.campus.ticketing.service.TerminationSettlementService;
import com.campus.ticketing.service.core.*;
import java.util.UUID;
import org.springframework.stereotype.Service;

@Service
public class TerminationSettlementServiceImpl implements TerminationSettlementService {
  private final BusinessSupport support;
  private final DurableWorkflows workflows;

  public TerminationSettlementServiceImpl(BusinessSupport support, DurableWorkflows workflows) {
    this.support = support;
    this.workflows = workflows;
  }

  @Override
  public void processPending(UUID jobId) {
    support.read(
        () -> {
          BusinessSupport.valid(
              support.need(JobEntity.class, jobId, false).type().equals("JOB-04"), "内部任务类型不匹配");
          return null;
        });
    workflows.process(jobId);
  }
}
