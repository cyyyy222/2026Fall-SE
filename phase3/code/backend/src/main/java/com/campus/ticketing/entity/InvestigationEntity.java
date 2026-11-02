package com.campus.ticketing.entity;

import java.util.UUID;

/** 管理员核查，同活动同事实去重。仅内部持久化使用；不得直接序列化为API响应。 */
public record InvestigationEntity(
    UUID id,
    UUID eventId,
    UUID investigatorId,
    String factKey,
    String conclusion,
    String reason,
    String evidenceText,
    Long createdAtMs,
    UUID terminationJobId) {}
