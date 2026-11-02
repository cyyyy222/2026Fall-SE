package com.campus.ticketing.entity;

import java.util.UUID;

/** 结算完成正式快照；比率从整数分子分母推导，不保存平均百分数。仅内部持久化使用；不得直接序列化为API响应。 */
public record StatisticsSnapshotEntity(
    UUID id,
    UUID eventId,
    UUID sessionId,
    String reason,
    Long generatedAtMs,
    Long issuedCount,
    Long refundCount,
    Long nonRefundedIssuedCount,
    Long checkedInCount,
    Long absentCount,
    Long capacityAtEnd,
    Long currentWaitlistCount,
    Long cumulativeWaitlistApplications) {}
