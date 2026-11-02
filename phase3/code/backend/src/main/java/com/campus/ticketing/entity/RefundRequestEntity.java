package com.campus.ticketing.entity;

import java.util.UUID;

/** T1合法退款依据；先受理后执行，失败仍可恢复。仅内部持久化使用；不得直接序列化为API响应。 */
public record RefundRequestEntity(
    UUID id,
    UUID orderId,
    UUID originalPaymentId,
    String source,
    String status,
    UUID initialAttemptId,
    UUID acceptedBy,
    Long acceptedAtMs,
    String reason,
    Long registrationClosesAtMs,
    String orderStatusAtAcceptance,
    String eligibilitySnapshotJson,
    String requestFingerprint,
    UUID idempotencyKey,
    UUID acceptedOperationId,
    UUID terminationFactId,
    Long revokedAtMs,
    String closureReason) {}
