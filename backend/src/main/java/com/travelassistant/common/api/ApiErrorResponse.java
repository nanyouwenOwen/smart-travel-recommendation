package com.travelassistant.common.api;

public record ApiErrorResponse(ApiError error, ApiMeta meta) {
  public static ApiErrorResponse of(ApiError error, String requestId) {
    return new ApiErrorResponse(error, new ApiMeta(requestId));
  }
}
