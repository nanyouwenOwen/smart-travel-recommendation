package com.travelassistant.common.logging;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class SensitiveDataSanitizerTest {
  @Test
  void masksBearerTokensAndSecretFields() {
    String input =
        "Authorization: Bearer abc.def X-API-Key: unique-fake-mimo-key "
            + "{\"password\":\"secret\",\"apiKey\":\"key-123\"}";

    String sanitized = SensitiveDataSanitizer.sanitize(input);

    assertThat(sanitized)
        .doesNotContain("abc.def", "secret", "key-123", "unique-fake-mimo-key")
        .contains("Bearer ***", "X-API-Key: ***", "\"password\":\"***\"", "\"apiKey\":\"***\"");
  }
}
