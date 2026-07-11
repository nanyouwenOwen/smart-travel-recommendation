package com.travelassistant.trip.ai;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(TripPlanningProperties.class)
public class TripPlanningAiConfiguration {}
