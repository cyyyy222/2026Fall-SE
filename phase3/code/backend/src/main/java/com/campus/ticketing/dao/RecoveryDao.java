package com.campus.ticketing.dao;

import com.campus.ticketing.entity.RecoveryEntity;
import java.util.*;

/** 持久层端口，事务由Service控制；尚未实现业务SQL。 */
public interface RecoveryDao extends EntityDao<RecoveryEntity> {
  Optional<RecoveryEntity> findOpen(String normalizedAccount, String phone);

  boolean updateWithStatus(RecoveryEntity value, String expectedStatus);
}
