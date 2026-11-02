package com.campus.ticketing.entity;

import java.util.UUID;

/** 当前授权及不可变授权历史；一个账号至多一个当前场次。仅内部持久化使用；不得直接序列化为API响应。 */
public record CheckerAssignmentEntity(
    UUID id,
    UUID checkerId,
    UUID sessionId,
    UUID assignedBy,
    Long assignedAtMs,
    Long revokedAtMs,
    String revocationReason) {}
