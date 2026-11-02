package com.campus.ticketing.entity;

import java.util.UUID;

/** 15天限制历史；到期实时判断，无人工任意解除。仅内部持久化使用；不得直接序列化为API响应。 */
public record RestrictionEntity(
    UUID id,
    UUID phoneIdentityId,
    String kind,
    Long startsAtMs,
    Long endsAtMs,
    String reason,
    UUID createdBy,
    Long endedMessageAtMs) {}
