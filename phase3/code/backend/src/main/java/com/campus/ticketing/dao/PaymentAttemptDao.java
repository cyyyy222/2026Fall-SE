package com.campus.ticketing.dao;

import com.campus.ticketing.entity.PaymentAttemptEntity;
import java.util.*;

/** 持久层端口，事务由Service控制；尚未实现业务SQL。 */
public interface PaymentAttemptDao extends EntityDao<PaymentAttemptEntity> {
  Optional<PaymentAttemptEntity> findByOrderAndAttempt(UUID orderId, UUID attemptId);

  Optional<PaymentAttemptEntity> findSuccessByOrder(UUID orderId);

  List<PaymentAttemptEntity> findByOrder(UUID orderId);
}
