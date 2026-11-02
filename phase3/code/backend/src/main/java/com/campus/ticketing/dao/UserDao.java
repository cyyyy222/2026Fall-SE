package com.campus.ticketing.dao;

import com.campus.ticketing.entity.UserEntity;
import java.util.*;

/** 持久层端口，事务由Service控制；尚未实现业务SQL。 */
public interface UserDao extends EntityDao<UserEntity> {
  Optional<UserEntity> findActiveByUsername(String normalizedUsername);

  List<UserEntity> lockEnabledAdministrators();

  boolean updateWithVersion(UserEntity user, long expectedVersion);
}
