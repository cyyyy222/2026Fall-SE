package com.campus.ticketing.entity;

import java.util.UUID;

/** 通用持久幂等；保存原结论和资源引用，不存令牌/私有报表响应。仅内部持久化使用；不得直接序列化为API响应。 */
public record OperationRecordEntity(
    UUID id,
    UUID actorId,
    String apiId,
    String targetKey,
    UUID idempotencyKey,
    String fingerprint,
    String phase,
    String resultCode,
    String resourceType,
    UUID resourceId,
    String resultMetadataJson,
    Long createdAtMs,
    Long expiresAtMs) {}
