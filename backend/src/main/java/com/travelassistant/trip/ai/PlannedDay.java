package com.travelassistant.trip.ai;

import java.time.LocalDate;
import java.util.List;

public record PlannedDay(
    int dayNumber, LocalDate date, String summary, List<PlannedActivity> activities) {}
