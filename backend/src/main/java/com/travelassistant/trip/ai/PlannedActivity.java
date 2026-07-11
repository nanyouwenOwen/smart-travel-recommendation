package com.travelassistant.trip.ai;
import com.travelassistant.trip.BudgetCategory; import java.math.BigDecimal; import java.time.LocalTime;
public record PlannedActivity(int sequenceNumber,LocalTime startTime,LocalTime endTime,String title,String location,String description,BigDecimal estimatedCost,BudgetCategory category,String transportAdvice) {}
