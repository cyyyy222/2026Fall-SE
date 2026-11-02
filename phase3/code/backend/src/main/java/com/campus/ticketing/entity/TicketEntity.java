package com.campus.ticketing.entity;

import java.util.UUID;

/** 全局唯一电子票；每订单只有一票。仅内部持久化使用；不得直接序列化为API响应。 */
public record TicketEntity(
    UUID id,
    UUID orderId,
    String status,
    Long issuedAtMs,
    Long invalidatedAtMs,
    String invalidReason) {}
