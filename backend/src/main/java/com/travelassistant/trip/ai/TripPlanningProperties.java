package com.travelassistant.trip.ai;

import jakarta.validation.constraints.*;
import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Validated
@ConfigurationProperties("app.trip-planning")
public record TripPlanningProperties(
    @NotBlank String provider,
    @NotBlank String promptVersion,
    @NotNull Duration connectTimeout,
    @NotNull Duration requestTimeout,
    @NotNull Duration totalTimeout,
    @Min(1) @Max(3) int maxAttempts,
    @Min(1) int globalConcurrency,
    @Min(1) int userRequestsPerMinute) {
  @AssertTrue(message = "AI 超时必须为正数且总截止不小于单次超时") public boolean isTimeoutValid() {
    return connectTimeout.isPositive()
        && requestTimeout.isPositive()
        && totalTimeout.isPositive()
        && !totalTimeout.minus(requestTimeout).isNegative();
  }
}
