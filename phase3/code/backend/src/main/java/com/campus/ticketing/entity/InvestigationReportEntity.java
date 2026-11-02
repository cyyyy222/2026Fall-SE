package com.campus.ticketing.entity;

import java.util.UUID;

/** 合并举报关联；不把关系数组塞进活动行。仅内部持久化使用；不得直接序列化为API响应。 */
public record InvestigationReportEntity(UUID id, UUID investigationId, UUID reportId) {}
