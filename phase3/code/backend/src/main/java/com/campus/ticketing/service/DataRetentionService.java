package com.campus.ticketing.service;

import java.util.UUID;

/** Internal persistent-job port; no executor implementation in review stage. */
public interface DataRetentionService {
  void cleanExpired(UUID jobId);
}
