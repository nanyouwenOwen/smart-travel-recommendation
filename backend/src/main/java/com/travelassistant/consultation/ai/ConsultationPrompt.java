package com.travelassistant.consultation.ai;import java.util.List;public record ConsultationPrompt(List<PromptMessage> messages){public record PromptMessage(String role,String content){}}
