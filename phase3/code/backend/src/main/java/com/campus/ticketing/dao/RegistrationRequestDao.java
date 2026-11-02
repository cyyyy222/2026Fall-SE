package com.campus.ticketing.dao;

import com.campus.ticketing.entity.RegistrationRequestEntity;
import java.util.*;

/** 持久层端口，事务由Service控制；尚未实现业务SQL。 */
public interface RegistrationRequestDao extends EntityDao<RegistrationRequestEntity> {
  Optional<RegistrationRequestEntity> findByUserAndRequest(UUID userId, UUID requestId);
}
