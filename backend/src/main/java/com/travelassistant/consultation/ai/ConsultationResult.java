package com.travelassistant.consultation.ai;

public record ConsultationResult(
    String content, String model, Integer inputTokens, Integer outputTokens) {}
