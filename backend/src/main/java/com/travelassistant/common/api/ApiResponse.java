package com.travelassistant.common.api;

public record ApiResponse<T>(T data, ApiMeta meta) {
  public static <T> ApiResponse<T> of(T data, String requestId) {
    return new ApiResponse<>(data, new ApiMeta(requestId));
  }
}
