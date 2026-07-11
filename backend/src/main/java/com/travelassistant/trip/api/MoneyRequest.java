package com.travelassistant.trip.api;

import jakarta.validation.constraints.*;

public record MoneyRequest(
    @NotBlank @Pattern(regexp = "^\\d+(\\.\\d{1,2})?$") String amount,
    @NotBlank @Pattern(regexp = "^[A-Z]{3}$") String currency) {}
