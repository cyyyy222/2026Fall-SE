package com.campus.ticketing.common;

import java.util.List;
import org.springframework.http.ResponseCookie;

/** Protocol metadata is separate from serialized data; raw session cookie is never cached. */
public record UseCaseResult<T>(int status, T data, List<ResponseCookie> cookies) {
  public UseCaseResult {
    if (status != 200 && status != 201 && status != 202)
      throw new IllegalArgumentException("Unexpected success status");
    cookies = List.copyOf(cookies);
  }
}
