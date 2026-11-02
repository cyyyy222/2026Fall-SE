package com.campus.ticketing.entity;

import java.util.UUID;

/** 180天物理清理审计；成功事实与业务同事务。仅内部持久化使用；不得直接序列化为API响应。 */
public record AuditLogEntity(
    UUID id,
    UUID actorId,
    String actorRole,
    String action,
    String objectType,
    UUID objectId,
    String result,
    String failureReason,
    String jobType,
    String traceId,
    Long createdAtMs) {}
