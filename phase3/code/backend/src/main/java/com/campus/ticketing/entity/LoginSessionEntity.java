package com.campus.ticketing.entity;

import java.util.UUID;

/** 固定七天、不滑动续期；历史撤销记录保留。仅内部持久化使用；不得直接序列化为API响应。 */
public record LoginSessionEntity(
    UUID id,
    UUID userId,
    String tokenHash,
    Long createdAtMs,
    Long expiresAtMs,
    Long revokedAtMs,
    String revocationReason) {}
