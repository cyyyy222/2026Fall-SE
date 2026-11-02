package com.campus.ticketing.dao;

import com.campus.ticketing.entity.JobEntity;
import java.util.*;

/** 持久层端口，事务由Service控制；尚未实现业务SQL。 */
public interface JobDao extends EntityDao<JobEntity> {
  Optional<JobEntity> findByTrigger(String type, String dedupKey);

  List<JobEntity> findDue(long nowMs, int limit);
}
