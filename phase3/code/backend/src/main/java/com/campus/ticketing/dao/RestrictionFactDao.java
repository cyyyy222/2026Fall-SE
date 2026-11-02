package com.campus.ticketing.dao;

import com.campus.ticketing.entity.RestrictionFactEntity;
import java.util.*;

/** 持久层端口，事务由Service控制；尚未实现业务SQL。 */
public interface RestrictionFactDao extends EntityDao<RestrictionFactEntity> {
  boolean isConsumed(UUID factId);
}
