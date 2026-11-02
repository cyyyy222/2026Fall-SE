package com.campus.ticketing.dao;

import com.campus.ticketing.entity.RefundAttemptEntity;
import java.util.*;

/** 持久层端口，事务由Service控制；尚未实现业务SQL。 */
public interface RefundAttemptDao extends EntityDao<RefundAttemptEntity> {
  Optional<RefundAttemptEntity> findByRequestAndAttempt(UUID requestId, UUID attemptId);

  Optional<RefundAttemptEntity> findSuccessByOrder(UUID orderId);

  List<RefundAttemptEntity> findByRequest(UUID requestId);
}
