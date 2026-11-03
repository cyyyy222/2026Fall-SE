package com.campus.ticketing.entity;

import java.util.UUID;

/** 活动配置；不在管理DTO泄漏过期订单计数。仅内部持久化使用；不得直接序列化为API响应。 */
public record EventEntity(
    UUID id,
    UUID organizerId,
    String status,
    String name,
    UUID categoryId,
    String categoryNameSnapshot,
    String description,
    UUID posterFileId,
    Long submittedAtMs,
    Long publishedAtMs,
    Long terminatedAtMs,
    String terminationReason,
    String lastReviewReason,
    String settlementStatus,
    Long createdAtMs,
    Long updatedAtMs,
    Long version,
    String proposedCategoryName) {
  public EventEntity(
      UUID id,
      UUID organizerId,
      String status,
      String name,
      UUID categoryId,
      String categoryNameSnapshot,
      String description,
      UUID posterFileId,
      Long submittedAtMs,
      Long publishedAtMs,
      Long terminatedAtMs,
      String terminationReason,
      String lastReviewReason,
      String settlementStatus,
      Long createdAtMs,
      Long updatedAtMs,
      Long version) {
    this(
        id,
        organizerId,
        status,
        name,
        categoryId,
        categoryNameSnapshot,
        description,
        posterFileId,
        submittedAtMs,
        publishedAtMs,
        terminatedAtMs,
        terminationReason,
        lastReviewReason,
        settlementStatus,
        createdAtMs,
        updatedAtMs,
        version,
        null);
  }
}
