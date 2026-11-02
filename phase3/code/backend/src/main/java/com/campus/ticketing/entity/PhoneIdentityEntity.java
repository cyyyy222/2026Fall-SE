package com.campus.ticketing.entity;

import java.util.UUID;

/** 长期手机号身份；注销不删除，跨账号继承黑名单和限制。仅内部持久化使用；不得直接序列化为API响应。 */
public record PhoneIdentityEntity(
    UUID id,
    String phone,
    Integer cancellationCount,
    Long lastCancelledAtMs,
    Long blacklistedAtMs,
    Long createdAtMs,
    Long version) {}
