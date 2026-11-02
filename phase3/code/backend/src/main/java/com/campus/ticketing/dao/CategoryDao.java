package com.campus.ticketing.dao;

import com.campus.ticketing.entity.CategoryEntity;
import java.util.*;

/** 持久层端口，事务由Service控制；尚未实现业务SQL。 */
public interface CategoryDao extends EntityDao<CategoryEntity> {
  List<CategoryEntity> findEnabled();

  boolean updateWithVersion(CategoryEntity category, long expectedVersion);
}
