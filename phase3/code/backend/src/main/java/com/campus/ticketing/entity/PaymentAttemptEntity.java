package com.campus.ticketing.entity;

import java.util.UUID;

/** 模拟支付尝试；每单最多一笔成功。仅内部持久化使用；不得直接序列化为API响应。 */
public record PaymentAttemptEntity(
    UUID id,
    UUID orderId,
    UUID attemptId,
    UUID operationId,
    String result,
    Long amountJiao,
    String failureReason,
    Long createdAtMs) {}
