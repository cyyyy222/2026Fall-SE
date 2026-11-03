package com.campus.ticketing.entity;

import java.util.UUID;

/** 参与者对限制的申诉；每次限制可补充证据再次申诉，但同一时间只有一条待审。 */
public record RestrictionAppealEntity(
    UUID id,
    UUID restrictionId,
    UUID userId,
    String reason,
    String evidenceText,
    String status,
    Long createdAtMs,
    UUID reviewedBy,
    Long reviewedAtMs,
    String decisionNote,
    Long version) {}
