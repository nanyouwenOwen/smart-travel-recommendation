package com.travelassistant.ai;

import java.util.List;

public record AnthropicMessagesRequest(
    String model, int max_tokens, String system, List<Message> messages, boolean stream) {
  public record Message(String role, String content) {}
}
