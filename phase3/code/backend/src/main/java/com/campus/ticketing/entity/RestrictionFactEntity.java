package com.campus.ticketing.entity;

import java.util.UUID;

/** 已消费事实；同一事实不能再次用于处罚。仅内部持久化使用；不得直接序列化为API响应。 */
public record RestrictionFactEntity(UUID id, UUID restrictionId, UUID factId) {}
