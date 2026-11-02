package com.campus.ticketing.entity;

import java.util.UUID;

/** 每个报名键永久指向原订单，包括返回已有有效单的新键。仅内部持久化使用；不得直接序列化为API响应。 */
public record RegistrationRequestEntity(
    UUID id,
    UUID userId,
    UUID requestId,
    String fingerprint,
    UUID orderId,
    UUID operationId,
    String operationResult,
    Long createdAtMs) {}
