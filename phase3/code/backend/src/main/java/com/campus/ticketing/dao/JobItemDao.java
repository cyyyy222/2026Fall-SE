package com.campus.ticketing.dao;

import com.campus.ticketing.entity.JobItemEntity;
import java.util.*;

/** 持久层端口，事务由Service控制；尚未实现业务SQL。 */
public interface JobItemDao extends EntityDao<JobItemEntity> {
  Optional<JobItemEntity> findByJobAndKey(UUID jobId, String itemKey);

  List<JobItemEntity> findPending(UUID jobId, long nowMs, int limit);
}
