package com.campus.ticketing.dao;

import com.campus.ticketing.entity.PhoneIdentityEntity;
import java.util.*;

/** 持久层端口，事务由Service控制；尚未实现业务SQL。 */
public interface PhoneIdentityDao extends EntityDao<PhoneIdentityEntity> {
  Optional<PhoneIdentityEntity> findByPhone(String normalizedPhone);

  Optional<PhoneIdentityEntity> lockByPhone(String normalizedPhone);
}
