package com.campus.ticketing.entity;

import java.util.UUID;

/** 场次；草稿删除使用逻辑删除保留检票授权审计。仅内部持久化使用；不得直接序列化为API响应。 */
public record EventSessionEntity(
    UUID id,
    UUID eventId,
    String name,
    Long startsAtMs,
    Long endsAtMs,
    String location,
    Integer capacity,
    Long salesOpensAtMs,
    Long registrationClosesAtMs,
    String settlementStatus,
    Long deletedAtMs,
    Long version) {}
