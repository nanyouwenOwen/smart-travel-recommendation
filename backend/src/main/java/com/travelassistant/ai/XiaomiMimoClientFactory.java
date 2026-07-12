package com.travelassistant.ai;

import java.time.Duration;
import org.springframework.http.HttpHeaders;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

public final class XiaomiMimoClientFactory {
  private XiaomiMimoClientFactory() {}

  public static RestClient create(
      XiaomiMimoProperties properties, Duration connectTimeout, Duration readTimeout) {
    properties.requireKey();
    SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
    factory.setConnectTimeout(connectTimeout);
    factory.setReadTimeout(readTimeout);
    return RestClient.builder()
        .baseUrl(properties.baseUrl())
        .requestFactory(factory)
        .defaultHeader("x-api-key", properties.apiKey())
        .defaultHeader("anthropic-version", properties.anthropicVersion())
        .defaultHeader(HttpHeaders.CONTENT_TYPE, "application/json")
        .build();
  }
}
