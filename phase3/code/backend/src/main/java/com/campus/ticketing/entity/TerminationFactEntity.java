package com.campus.ticketing.entity;

import java.util.UUID;

/** 终止最初生效时逐订单结算依据；不按恢复时刻重新判定退款。仅内部持久化使用；不得直接序列化为API响应。 */
public record TerminationFactEntity(
    UUID id,
    UUID eventId,
    UUID jobId,
    UUID orderId,
    Long effectiveAtMs,
    String statusAtEffective,
    Long sessionEndsAtMs,
    String settlementAction,
    String reason) {}
