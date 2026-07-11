package com.travelassistant.trip.api;

import jakarta.validation.constraints.*;

public record AdjustTripRequest(@NotBlank @Size(min = 2, max = 2000) String instruction) {}
