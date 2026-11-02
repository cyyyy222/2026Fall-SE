package com.campus.ticketing.entity;

import java.util.UUID;

/** 票种配置；余票按权威订单派生，无Redis库存。仅内部持久化使用；不得直接序列化为API响应。 */
public record TicketTypeEntity(
    UUID id,
    UUID sessionId,
    String name,
    Integer capacity,
    Long priceJiao,
    Boolean saleStopped,
    Long deletedAtMs,
    Long version) {}
