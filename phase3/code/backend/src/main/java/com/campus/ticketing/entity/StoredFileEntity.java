package com.campus.ticketing.entity;

import java.util.UUID;

/** 受控海报/CSV元数据；文件不直接静态开放。仅内部持久化使用；不得直接序列化为API响应。 */
public record StoredFileEntity(
    UUID id,
    UUID ownerId,
    String kind,
    String storageKey,
    String contentType,
    Long byteSize,
    String sha256,
    Long createdAtMs,
    Long deletedAtMs) {}
