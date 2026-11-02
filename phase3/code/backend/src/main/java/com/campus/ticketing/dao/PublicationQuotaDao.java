package com.campus.ticketing.dao;

import com.campus.ticketing.entity.PublicationQuotaEntity;
import java.util.*;

/** 持久层端口，事务由Service控制；尚未实现业务SQL。 */
public interface PublicationQuotaDao extends EntityDao<PublicationQuotaEntity> {
  Optional<PublicationQuotaEntity> lockByDay(java.time.LocalDate beijingDay);

  boolean incrementIfBelowLimit(java.time.LocalDate day);
}
