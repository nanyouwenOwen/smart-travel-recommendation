package com.travelassistant.ai;

import java.net.URI;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("app.ai.xiaomi-mimo")
public record XiaomiMimoProperties(
    String baseUrl, String apiKey, String model, String anthropicVersion, int maxOutputTokens) {
  public XiaomiMimoProperties {
    URI uri = baseUrl == null ? null : URI.create(baseUrl);
    if (uri == null
        || !uri.isAbsolute()
        || uri.isOpaque()
        || uri.getHost() == null
        || uri.getUserInfo() != null
        || uri.getQuery() != null
        || uri.getFragment() != null
        || !(uri.getPath().equals("/anthropic") || uri.getPath().equals("/anthropic/"))
        || (!("https".equals(uri.getScheme()))
            && !("http".equals(uri.getScheme()) && "localhost".equals(uri.getHost()))))
      throw new IllegalArgumentException("XIAOMI_MIMO_BASE_URL must use HTTPS");
    if (model == null || model.isBlank())
      throw new IllegalArgumentException("XIAOMI_MIMO_MODEL is required");
    if (anthropicVersion == null || anthropicVersion.isBlank())
      throw new IllegalArgumentException("XIAOMI_MIMO_ANTHROPIC_VERSION is required");
    if (maxOutputTokens < 1 || maxOutputTokens > 65536)
      throw new IllegalArgumentException("XIAOMI_MIMO_MAX_OUTPUT_TOKENS is invalid");
    baseUrl = baseUrl.replaceAll("/+$", "");
  }

  public void requireKey() {
    if (apiKey == null || apiKey.isBlank())
      throw new IllegalStateException("Xiaomi MiMo provider requires XIAOMI_MIMO_API_KEY");
  }
}
