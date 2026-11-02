package com.campus.ticketing.entity;

import java.util.UUID;

/** 逐单/逐接收人待办；无订单的组织者/检票员也必须收通知。仅内部持久化使用；不得直接序列化为API响应。 */
public record JobItemEntity(
    UUID id,
    UUID jobId,
    String itemKey,
    String kind,
    UUID orderId,
    UUID recipientId,
    String status,
    String payloadJson,
    Integer retryCount,
    Long nextAttemptAtMs,
    Long completedAtMs,
    String lastError) {}
