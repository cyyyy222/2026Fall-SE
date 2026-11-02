package com.campus.ticketing.entity;

import java.util.UUID;

/** 异步CSV；元数据受活动终结加七天动态边界保护。仅内部持久化使用；不得直接序列化为API响应。 */
public record ExportEntity(
    UUID id,
    UUID eventId,
    UUID createdBy,
    UUID jobId,
    String status,
    UUID sessionId,
    UUID ticketTypeId,
    String orderStatusFilter,
    String checkinStatusFilter,
    Long rowCount,
    UUID fileId,
    Long createdAtMs,
    Long completedAtMs,
    String failureReason) {}
