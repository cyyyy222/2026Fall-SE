package com.campus.ticketing.dao;

import com.campus.ticketing.entity.OrderEntity;
import java.util.*;

/** 持久层端口，事务由Service控制；尚未实现业务SQL。 */
public interface OrderDao extends EntityDao<OrderEntity> {
  Optional<OrderEntity> findActiveByUserAndSession(UUID userId, UUID sessionId);

  List<OrderEntity> findWaitingInOrder(UUID ticketTypeId, int limit);

  long countOccupyingByType(UUID ticketTypeId);

  boolean updateWithVersion(OrderEntity order, long expectedVersion);
}
