package com.travelassistant.consultation.ai;

import java.util.function.Consumer;

public interface ConsultationProvider {
  ConsultationResult answer(ConsultationPrompt prompt);

  ConsultationResult stream(
      ConsultationPrompt prompt, Consumer<String> chunks, CancellationToken cancellation);

  String providerName();

  String modelName();
}
