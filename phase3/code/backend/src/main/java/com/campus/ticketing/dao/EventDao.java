package com.campus.ticketing.dao;

import com.campus.ticketing.entity.EventEntity;
import java.util.*;

/** 持久层端口，事务由Service控制；尚未实现业务SQL。 */
public interface EventDao extends EntityDao<EventEntity> {
  boolean updateWithVersion(EventEntity event, long expectedVersion);

  List<EventEntity> findPendingReviews(int limit, long offset);
}
