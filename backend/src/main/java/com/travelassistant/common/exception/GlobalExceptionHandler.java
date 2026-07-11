package com.travelassistant.common.exception;

import com.travelassistant.common.api.ApiError;
import com.travelassistant.common.api.ApiErrorResponse;
import com.travelassistant.common.api.ApiFieldError;
import com.travelassistant.common.web.RequestIdFilter;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.ConstraintViolationException;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.security.access.AccessDeniedException;

@RestControllerAdvice
public class GlobalExceptionHandler {
    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(MethodArgumentNotValidException.class)
    ResponseEntity<ApiErrorResponse> validation(MethodArgumentNotValidException exception,
                                                HttpServletRequest request) {
        List<ApiFieldError> details = exception.getBindingResult().getFieldErrors().stream()
                .map(this::toApiFieldError)
                .toList();
        return response(HttpStatus.BAD_REQUEST,
                new ApiError("VALIDATION_ERROR", "请求参数不合法", details), request);
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    ResponseEntity<ApiErrorResponse> malformedJson(HttpMessageNotReadableException exception,
                                                   HttpServletRequest request) {
        return response(HttpStatus.BAD_REQUEST,
                new ApiError("MALFORMED_JSON", "请求内容不是有效的 JSON"), request);
    }

    @ExceptionHandler(ConstraintViolationException.class)
    ResponseEntity<ApiErrorResponse> constraintViolation(ConstraintViolationException exception,
                                                          HttpServletRequest request) {
        List<ApiFieldError> details = exception.getConstraintViolations().stream()
                .map(violation -> new ApiFieldError(violation.getPropertyPath().toString(),
                        violation.getMessage()))
                .toList();
        return response(HttpStatus.BAD_REQUEST,
                new ApiError("VALIDATION_ERROR", "请求参数不合法", details), request);
    }

    @ExceptionHandler(BusinessException.class)
    ResponseEntity<ApiErrorResponse> business(BusinessException exception, HttpServletRequest request) {
        return response(exception.getStatus(),
                new ApiError(exception.getCode(), exception.getMessage()), request);
    }

    @ExceptionHandler(AccessDeniedException.class)
    ResponseEntity<ApiErrorResponse> accessDenied(AccessDeniedException exception, HttpServletRequest request) {
        return response(HttpStatus.FORBIDDEN, new ApiError("FORBIDDEN", "无权执行此操作"), request);
    }

    @ExceptionHandler(Exception.class)
    ResponseEntity<ApiErrorResponse> unexpected(Exception exception, HttpServletRequest request) {
        log.error("Unhandled request failure type={}", exception.getClass().getName());
        return response(HttpStatus.INTERNAL_SERVER_ERROR,
                new ApiError("INTERNAL_ERROR", "服务暂时不可用"), request);
    }

    private ApiFieldError toApiFieldError(FieldError error) {
        return new ApiFieldError(error.getField(), error.getDefaultMessage());
    }

    private ResponseEntity<ApiErrorResponse> response(HttpStatus status, ApiError error,
                                                      HttpServletRequest request) {
        String requestId = (String) request.getAttribute(RequestIdFilter.ATTRIBUTE);
        return ResponseEntity.status(status).body(ApiErrorResponse.of(error, requestId));
    }
}
