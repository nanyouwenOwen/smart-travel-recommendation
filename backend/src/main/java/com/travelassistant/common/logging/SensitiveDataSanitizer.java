package com.travelassistant.common.logging;

import java.util.regex.Pattern;

public final class SensitiveDataSanitizer {
  private static final Pattern BEARER = Pattern.compile("(?i)Bearer\\s+[A-Za-z0-9._~+/=-]+");
  private static final Pattern X_API_KEY =
      Pattern.compile("(?i)(x-api-key\\s*[:=]\\s*)[^\\s,;\"}]+");
  private static final Pattern SECRET_FIELD =
      Pattern.compile(
          "(?i)(\\\"(?:password|accessToken|refreshToken|apiKey)\\\"\\s*:\\s*\\\")[^\\\"]*(\\\")");

  private SensitiveDataSanitizer() {}

  public static String sanitize(String value) {
    if (value == null) {
      return null;
    }
    String withoutBearer = BEARER.matcher(value).replaceAll("Bearer ***");
    String withoutXApiKey = X_API_KEY.matcher(withoutBearer).replaceAll("$1***");
    return SECRET_FIELD.matcher(withoutXApiKey).replaceAll("$1***$2");
  }
}
