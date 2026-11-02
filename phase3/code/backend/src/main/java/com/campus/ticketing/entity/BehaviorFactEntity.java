package com.campus.ticketing.entity;

import java.util.UUID;

/** 长期处罚依据；正常缺席/主动成功退款/组织者违规。仅内部持久化使用；不得直接序列化为API响应。 */
public record BehaviorFactEntity(
    UUID id,
    UUID phoneIdentityId,
    String type,
    UUID orderId,
    UUID investigationId,
    Long occurredAtMs,
    String reason) {}
