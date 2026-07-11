package com.travelassistant.trip.ai;
import java.util.List;
public record TripPlan(List<PlannedDay> days,List<String> warnings) {}
