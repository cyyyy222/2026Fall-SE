package com.campus.ticketing.entity;

import java.util.UUID;

/** 单人单票订单；排队/占位/历史快照。仅内部持久化使用；不得直接序列化为API响应。 */
public record OrderEntity(
    UUID id,
    UUID requestId,
    UUID userId,
    UUID phoneIdentityId,
    UUID eventId,
    UUID sessionId,
    UUID ticketTypeId,
    String status,
    String statusReason,
    String attendeeNameSnapshot,
    String eventNameSnapshot,
    String sessionNameSnapshot,
    String categoryNameSnapshot,
    String ticketTypeNameSnapshot,
    Long amountJiao,
    Long createdAtMs,
    Long waitlistedAtMs,
    Long offeredAtMs,
    Long confirmDeadlineMs,
    Long payDeadlineMs,
    Integer consecutivePaymentFailures,
    Long updatedAtMs,
    Long version) {}
