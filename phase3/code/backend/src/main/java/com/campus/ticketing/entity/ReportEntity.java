package com.campus.ticketing.entity;

import java.util.UUID;

/** 用户举报及额度计数原始事实；驳回不返还额度。仅内部持久化使用；不得直接序列化为API响应。 */
public record ReportEntity(
    UUID id,
    UUID reporterId,
    UUID eventId,
    UUID sessionId,
    String reason,
    String description,
    String evidenceText,
    String status,
    Long createdAtMs,
    String conclusion,
    Long resolvedAtMs) {}
