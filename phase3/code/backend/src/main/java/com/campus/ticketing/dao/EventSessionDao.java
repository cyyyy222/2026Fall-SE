package com.campus.ticketing.dao;

import com.campus.ticketing.entity.EventSessionEntity;
import java.util.*;

/** 持久层端口，事务由Service控制；尚未实现业务SQL。 */
public interface EventSessionDao extends EntityDao<EventSessionEntity> {
  List<EventSessionEntity> findActiveByEvent(UUID eventId);

  boolean updateWithVersion(EventSessionEntity session, long expectedVersion);
}
