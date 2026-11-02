package com.campus.ticketing.dao;

import com.campus.ticketing.entity.TicketTypeEntity;
import java.util.*;

/** 持久层端口，事务由Service控制；尚未实现业务SQL。 */
public interface TicketTypeDao extends EntityDao<TicketTypeEntity> {
  List<TicketTypeEntity> findActiveBySession(UUID sessionId);

  boolean updateWithVersion(TicketTypeEntity type, long expectedVersion);
}
