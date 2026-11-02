package com.campus.ticketing.scheduler;

import java.util.UUID;

/** No @Scheduled implementation until durable business processing is reviewed. */
public interface JobDispatcher {
  void processTimeouts(UUID jobId);

  void promoteWaitlist(UUID jobId);

  void settleNaturalEnd(UUID jobId);

  void settleTermination(UUID jobId);

  void cleanupRetention(UUID jobId);

  void reconcileRestrictions(UUID jobId);

  void generateExport(UUID jobId);
}
