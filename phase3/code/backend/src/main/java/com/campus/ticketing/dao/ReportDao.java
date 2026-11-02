package com.campus.ticketing.dao;

import com.campus.ticketing.entity.ReportEntity;
import java.util.*;

/** 持久层端口，事务由Service控制；尚未实现业务SQL。 */
public interface ReportDao extends EntityDao<ReportEntity> {
  long countAcceptedSince(UUID reporterId, long exclusiveFromMs, long inclusiveToMs);

  long countByEvent(UUID eventId);
}
