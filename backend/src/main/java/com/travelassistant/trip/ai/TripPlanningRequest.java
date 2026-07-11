package com.travelassistant.trip.ai;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

public record TripPlanningRequest(
    String destination,
    LocalDate startDate,
    int days,
    int travelers,
    BigDecimal budget,
    String currency,
    String timezone,
    List<String> preferences,
    String additionalRequirements,
    String adjustmentInstruction,
    TripPlan basePlan) {}
