package com.campus.ticketing.dao;

import com.campus.ticketing.entity.RestrictionEntity;
import java.util.*;

/** 持久层端口，事务由Service控制；尚未实现业务SQL。 */
public interface RestrictionDao extends EntityDao<RestrictionEntity> {
  List<RestrictionEntity> findEffective(UUID identityId, String kind, long nowMs);
}
