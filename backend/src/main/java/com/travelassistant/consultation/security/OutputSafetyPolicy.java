package com.travelassistant.consultation.security;

import java.util.regex.Pattern;
import org.springframework.stereotype.Component;

@Component
public class OutputSafetyPolicy {
  private static final Pattern SECRET =
      Pattern.compile(
          "(?i)(sk-[a-z0-9_-]{12,}|api[_-]?key\\s*[:=]\\s*\\S+|\\b(?:\\d[ -]*?){13,19}\\b)");

  public void check(String text) {
    if (SECRET.matcher(text).find() || text.contains("制作炸弹"))
      throw new UnsafeContentException("CONTENT_REJECTED", "AI 输出被安全策略拦截");
  }
}
