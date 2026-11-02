package com.campus.ticketing.dao;

import com.campus.ticketing.entity.MessageEntity;
import java.util.*;

/** 持久层端口，事务由Service控制；尚未实现业务SQL。 */
public interface MessageDao extends EntityDao<MessageEntity> {
  Optional<MessageEntity> findByEventAndRecipient(String eventKey, UUID recipientId);

  boolean markReadIfUnread(UUID messageId, long readAtMs);
}
