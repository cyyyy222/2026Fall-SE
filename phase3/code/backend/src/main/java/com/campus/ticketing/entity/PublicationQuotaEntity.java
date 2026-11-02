package com.campus.ticketing.entity;

import java.time.LocalDate;
import java.util.UUID;

/** 全站北京时间每日100次成功发布额度；非组织者单独100次。仅内部持久化使用；不得直接序列化为API响应。 */
public record PublicationQuotaEntity(
    UUID id, LocalDate day, Integer publishedCount, Long version) {}
