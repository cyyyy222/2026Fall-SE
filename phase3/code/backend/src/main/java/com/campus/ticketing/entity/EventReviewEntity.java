package com.campus.ticketing.entity;

import java.util.UUID;

/** 每次审核结论及退回理由。仅内部持久化使用；不得直接序列化为API响应。 */
public record EventReviewEntity(
    UUID id,
    UUID eventId,
    UUID reviewerId,
    Long submittedVersion,
    String outcome,
    String reasonCode,
    String reason,
    Long createdAtMs) {}
