package com.campus.ticketing.service.impl;

import com.campus.ticketing.entity.JobEntity;
import com.campus.ticketing.service.WaitlistService;
import com.campus.ticketing.service.core.*;
import java.util.UUID;
import org.springframework.stereotype.Service;

@Service
public class WaitlistServiceImpl implements WaitlistService {
  private final BusinessSupport support;
  private final DurableWorkflows workflows;

  public WaitlistServiceImpl(BusinessSupport support, DurableWorkflows workflows) {
    this.support = support;
    this.workflows = workflows;
  }

  @Override
  public void promoteEligible(UUID jobId) {
    support.read(
        () -> {
          BusinessSupport.valid(
              support.need(JobEntity.class, jobId, false).type().equals("JOB-02"), "内部任务类型不匹配");
          return null;
        });
    workflows.process(jobId);
  }
}
