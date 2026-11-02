package com.campus.ticketing.service;

import java.util.UUID;

/** Internal persistent-job port; no executor implementation in review stage. */
public interface NaturalSettlementService {
  void settleDueSessions(UUID jobId);
}
