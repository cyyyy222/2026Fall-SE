package com.campus.ticketing.dao;

import com.campus.ticketing.entity.OperationRecordEntity;
import java.util.*;

/** 持久层端口，事务由Service控制；尚未实现业务SQL。 */
public interface OperationRecordDao extends EntityDao<OperationRecordEntity> {
  Optional<OperationRecordEntity> findByKey(
      UUID actorId, String apiId, String targetKey, UUID idempotencyKey);
}
