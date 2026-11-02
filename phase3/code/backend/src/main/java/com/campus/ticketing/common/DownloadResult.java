package com.campus.ticketing.common;

import org.springframework.core.io.Resource;
import org.springframework.http.MediaType;

/** Resource is released only after Service authorization and retention checks succeed. */
public record DownloadResult(
    Resource resource, MediaType contentType, String filename, boolean attachment) {}
