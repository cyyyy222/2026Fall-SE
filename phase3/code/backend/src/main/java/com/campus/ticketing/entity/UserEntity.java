package com.campus.ticketing.entity;

import java.util.UUID;

/** 账号；逻辑删除释放登录标识，停用不释放。仅内部持久化使用；不得直接序列化为API响应。 */
public record UserEntity(
    UUID id,
    String username,
    String usernameNormalized,
    String email,
    String emailNormalized,
    UUID phoneIdentityId,
    String passwordHash,
    String role,
    String displayName,
    Boolean enabled,
    Boolean mustChangePassword,
    Long deletedAtMs,
    String unavailableReason,
    Long createdAtMs,
    Long updatedAtMs,
    Long version) {}
