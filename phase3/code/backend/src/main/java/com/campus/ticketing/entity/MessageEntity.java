package com.campus.ticketing.entity;

import java.util.UUID;

/** 持久站内消息；180天逻辑可读，首次已读不延长动作时限。仅内部持久化使用；不得直接序列化为API响应。 */
public record MessageEntity(
    UUID id,
    UUID recipientId,
    String eventKey,
    String type,
    String title,
    String body,
    String resourceType,
    UUID resourceId,
    Long createdAtMs,
    Long expiresAtMs,
    Long readAtMs,
    Long actionDeadlineMs) {}
