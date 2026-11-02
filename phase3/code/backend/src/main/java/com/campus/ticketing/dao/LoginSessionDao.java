package com.campus.ticketing.dao;

import com.campus.ticketing.entity.LoginSessionEntity;
import java.util.*;

/** 持久层端口，事务由Service控制；尚未实现业务SQL。 */
public interface LoginSessionDao extends EntityDao<LoginSessionEntity> {
  Optional<LoginSessionEntity> findByTokenHash(String sha256);

  int revokeAllForUser(UUID userId, long revokedAtMs, String reason);
}
