package com.campus.ticketing.entity;

import java.util.UUID;

/** 类别；停用不释放名称。仅内部持久化使用；不得直接序列化为API响应。 */
public record CategoryEntity(
    UUID id,
    String name,
    String nameNormalized,
    Boolean enabled,
    Long version,
    Long createdAtMs,
    Long updatedAtMs) {}
