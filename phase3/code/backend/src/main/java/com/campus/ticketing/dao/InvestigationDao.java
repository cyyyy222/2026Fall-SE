package com.campus.ticketing.dao;

import com.campus.ticketing.entity.InvestigationEntity;
import java.util.*;

/** 持久层端口，事务由Service控制；尚未实现业务SQL。 */
public interface InvestigationDao extends EntityDao<InvestigationEntity> {
  Optional<InvestigationEntity> findByEventAndFactKey(UUID eventId, String factKey);
}
