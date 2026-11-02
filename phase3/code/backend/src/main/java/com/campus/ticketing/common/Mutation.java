package com.campus.ticketing.common;

import java.util.UUID;

public record Mutation<T>(UUID operationId, String operationResult, boolean replayed, T current) {}
