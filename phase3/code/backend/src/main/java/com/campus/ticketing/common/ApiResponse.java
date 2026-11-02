package com.campus.ticketing.common;

import java.time.OffsetDateTime;

public record ApiResponse<T>(
    String code, String message, T data, String traceId, OffsetDateTime serverTime) {}
