package com.campus.ticketing.entity;

import java.util.UUID;

/** T2执行记录；失败不使原单/票/库存改变。仅内部持久化使用；不得直接序列化为API响应。 */
public record RefundAttemptEntity(
    UUID id,
    UUID requestId,
    UUID orderId,
    UUID originalPaymentId,
    UUID attemptId,
    UUID operationId,
    String result,
    Long amountJiao,
    String failureReason,
    Long createdAtMs) {}
