package com.campus.ticketing.dao;

import com.campus.ticketing.entity.BehaviorFactEntity;
import java.util.*;

/** 持久层端口，事务由Service控制；尚未实现业务SQL。 */
public interface BehaviorFactDao extends EntityDao<BehaviorFactEntity> {
  List<BehaviorFactEntity> findByIdentityAndWindow(
      UUID identityId, long exclusiveFromMs, long inclusiveToMs);
}
