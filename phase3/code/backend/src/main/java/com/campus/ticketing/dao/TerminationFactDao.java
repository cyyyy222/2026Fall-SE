package com.campus.ticketing.dao;

import com.campus.ticketing.entity.TerminationFactEntity;
import java.util.*;

/** 持久层端口，事务由Service控制；尚未实现业务SQL。 */
public interface TerminationFactDao extends EntityDao<TerminationFactEntity> {
  Optional<TerminationFactEntity> findByEventAndOrder(UUID eventId, UUID orderId);
}
