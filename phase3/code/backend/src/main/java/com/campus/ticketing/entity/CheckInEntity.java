package com.campus.ticketing.entity;

import java.util.UUID;

/** 不可变首次签到事实；保留当时检票员与场次。仅内部持久化使用；不得直接序列化为API响应。 */
public record CheckInEntity(
    UUID id,
    UUID ticketId,
    UUID orderId,
    UUID checkerId,
    UUID sessionId,
    UUID assignmentId,
    Long checkedAtMs) {}
