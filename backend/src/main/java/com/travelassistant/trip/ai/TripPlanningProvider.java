package com.travelassistant.trip.ai;
public interface TripPlanningProvider { TripPlan generate(TripPlanningRequest request); String providerName(); String modelName(); }
