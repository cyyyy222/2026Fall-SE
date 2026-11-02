package com.campus.ticketing.entity;

import java.util.UUID;

/** JOB-01至07持久任务；只查看，无HTTP任意执行入口。仅内部持久化使用；不得直接序列化为API响应。 */
public record JobEntity(
    UUID id,
    String type,
    String dedupKey,
    UUID eventId,
    String status,
    Long dueAtMs,
    Long deadlineAtMs,
    Long completedAtMs,
    Integer total,
    Integer processed,
    Integer retryCount,
    Long nextAttemptAtMs,
    String leaseOwner,
    Long leaseUntilMs,
    String lastError,
    Long createdAtMs) {}
