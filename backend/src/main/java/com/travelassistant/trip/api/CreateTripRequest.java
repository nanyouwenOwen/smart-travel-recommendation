package com.travelassistant.trip.api;

import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.time.LocalDate;
import java.util.List;

public record CreateTripRequest(
    @NotBlank @Size(max = 200) String destination,
    @NotNull LocalDate startDate,
    @Min(1) @Max(30) int days,
    @NotNull @Valid MoneyRequest budget,
    @Min(1) @Max(50) int travelers,
    @NotNull @Size(max = 20) List<@NotBlank @Size(max = 50) String> preferences,
    @NotBlank @Size(max = 64) String timezone,
    @Size(max = 2000) String additionalRequirements,
    @Pattern(regexp = "[0-9a-fA-F-]{36}") String destinationLocationId) {
  public CreateTripRequest(
      String destination,
      LocalDate startDate,
      int days,
      MoneyRequest budget,
      int travelers,
      List<String> preferences,
      String timezone,
      String additionalRequirements) {
    this(
        destination,
        startDate,
        days,
        budget,
        travelers,
        preferences,
        timezone,
        additionalRequirements,
        null);
  }
}
