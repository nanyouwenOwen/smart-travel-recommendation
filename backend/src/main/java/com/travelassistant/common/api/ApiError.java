package com.travelassistant.common.api;

import java.util.List;

public record ApiError(String code, String message, List<ApiFieldError> details) {
    public ApiError(String code, String message) {
        this(code, message, List.of());
    }
}

