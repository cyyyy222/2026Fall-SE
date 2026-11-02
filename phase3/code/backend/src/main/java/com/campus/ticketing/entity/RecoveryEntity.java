package com.campus.ticketing.entity;

import java.util.UUID;

/** 人工找回；受理时固定目标账号，防同名重注册接管。仅内部持久化使用；不得直接序列化为API响应。 */
public record RecoveryEntity(
    UUID id,
    String account,
    String accountNormalized,
    String phone,
    UUID targetAccountId,
    String status,
    String verificationNote,
    UUID verifiedBy,
    Long verifiedAtMs,
    Long createdAtMs,
    Long resolvedAtMs) {}
